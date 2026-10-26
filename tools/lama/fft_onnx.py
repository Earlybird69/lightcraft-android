"""ONNX export of LaMa's 2-D real FFTs without the ONNX DFT op.

LaMa's Fourier units call torch.fft.rfftn / irfftn over the last two dims with norm="ortho"; the
TorchScript exporter has no symbolic for them. These symbolics rebuild both transforms as matrix
products against DFT matrices generated in the graph from the tensor's own shape (Range, Cos,
Sin, MatMul), so the model keeps dynamic input sizes and runs on any ONNX Runtime build.
A complex tensor is carried as a real tensor with a trailing dimension of 2 (re, im).
"""
import math

import torch
from torch.onnx import register_custom_op_symbolic
from torch.onnx import symbolic_helper as sh

OPSET = 17


def _c(g, v, dtype=torch.int64):
    return g.op("Constant", value_t=torch.tensor(v, dtype=dtype))


def _dim(g, x, axis):
    """x.shape[axis] as an int64 scalar."""
    return g.op("Gather", g.op("Shape", x), _c(g, axis), axis_i=0)


def _f(g, v):
    return g.op("Cast", v, to_i=1)  # float32


def _angles(g, n, rows, cols):
    """2*pi*((r*c) mod n)/n for r in range(rows), c in range(cols): [rows, cols] float.

    The product is reduced mod n in integers first, so the angle stays below 2*pi and float32
    cos/sin stay accurate at large sizes.
    """
    r = g.op("Range", _c(g, 0), rows, _c(g, 1))
    c = g.op("Range", _c(g, 0), cols, _c(g, 1))
    outer = g.op("Mod", g.op("Mul", g.op("Unsqueeze", r, _c(g, [1])), g.op("Unsqueeze", c, _c(g, [0]))), n)
    return g.op("Mul", _f(g, outer), g.op("Div", _c(g, 2 * math.pi, torch.float32), _f(g, n)))


def _last(g, y, i):
    return g.op("Squeeze", g.op("Gather", y, _c(g, [i]), axis_i=-1), _c(g, [-1]))


def _pack(g, re, im):
    return g.op("Concat", g.op("Unsqueeze", re, _c(g, [-1])), g.op("Unsqueeze", im, _c(g, [-1])), axis_i=-1)


def _check(dim, norm, what):
    if norm not in ("ortho",):
        raise RuntimeError(f"{what}: norm {norm!r} not supported")
    if dim is not None and sorted(d % 4 if d < 0 else d for d in dim) != [2, 3]:
        raise RuntimeError(f"{what}: only the last two dims of a 4-D tensor are supported, got {dim}")


@sh.parse_args("v", "v", "is", "s")
def rfftn(g, x, s, dim, norm):
    _check(dim, norm, "rfftn")
    H, W = _dim(g, x, 2), _dim(g, x, 3)
    Wh = g.op("Add", g.op("Div", W, _c(g, 2)), _c(g, 1))
    aw = _angles(g, W, W, Wh)  # [W, Wh]
    re1 = g.op("MatMul", x, g.op("Cos", aw))
    im1 = g.op("Neg", g.op("MatMul", x, g.op("Sin", aw)))
    ah = _angles(g, H, H, H)  # [H, H], symmetric
    ch, shh = g.op("Cos", ah), g.op("Sin", ah)
    # (C - iS)(re + i im) = (C re + S im) + i (C im - S re)
    re2 = g.op("Add", g.op("MatMul", ch, re1), g.op("MatMul", shh, im1))
    im2 = g.op("Sub", g.op("MatMul", ch, im1), g.op("MatMul", shh, re1))
    scale = g.op("Reciprocal", g.op("Sqrt", _f(g, g.op("Mul", H, W))))
    return _pack(g, g.op("Mul", re2, scale), g.op("Mul", im2, scale))


@sh.parse_args("v", "v", "is", "s")
def irfftn(g, y, s, dim, norm):
    _check(dim, norm, "irfftn")
    sizes = sh._unpack_list(s) if sh._is_packed_list(s) else None
    if sizes is None or len(sizes) != 2:
        raise RuntimeError("irfftn: needs the output size s=[H, W]")
    H, W = (g.op("Reshape", v, _c(g, [])) for v in sizes)
    re, im = _last(g, y, 0), _last(g, y, 1)  # [B, C, H, Wh]
    Wh = _dim(g, re, 3)
    ah = _angles(g, H, H, H)
    ch, shh = g.op("Cos", ah), g.op("Sin", ah)
    # (C + iS)(re + i im) = (C re - S im) + i (C im + S re)
    re1 = g.op("Sub", g.op("MatMul", ch, re), g.op("MatMul", shh, im))
    im1 = g.op("Add", g.op("MatMul", ch, im), g.op("MatMul", shh, re))
    # complex-to-real along W from the one-sided spectrum: weights 1 for DC (and Nyquist), 2 otherwise
    j = g.op("Range", _c(g, 0), Wh, _c(g, 1))
    dc = g.op("Equal", j, _c(g, 0))
    nyq = g.op("Equal", g.op("Mul", j, _c(g, 2)), W)
    wt = g.op("Sub", g.op("Sub", _c(g, 2.0, torch.float32), _f(g, dc)), _f(g, nyq))
    aw = _angles(g, W, Wh, W)  # [Wh, W]
    cw = g.op("Mul", g.op("Cos", aw), g.op("Unsqueeze", wt, _c(g, [1])))
    sw = g.op("Mul", g.op("Sin", aw), g.op("Unsqueeze", wt, _c(g, [1])))
    out = g.op("Sub", g.op("MatMul", re1, cw), g.op("MatMul", im1, sw))
    scale = g.op("Reciprocal", g.op("Sqrt", _f(g, g.op("Mul", H, W))))
    return g.op("Mul", out, scale)


def real(g, y):
    return _last(g, y, 0)


def imag(g, y):
    return _last(g, y, 1)


def complex_(g, re, im):
    return _pack(g, re, im)


def register():
    register_custom_op_symbolic("aten::fft_rfftn", rfftn, OPSET)
    register_custom_op_symbolic("aten::fft_irfftn", irfftn, OPSET)
    register_custom_op_symbolic("aten::real", real, OPSET)
    register_custom_op_symbolic("aten::imag", imag, OPSET)
    register_custom_op_symbolic("aten::complex", complex_, OPSET)
