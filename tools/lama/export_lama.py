"""Export big-lama (TorchScript) to ONNX with dynamic H, W, then check it against PyTorch.

Usage: python3 export_lama.py big-lama.pt lama.onnx
Input  image: float32 [1, 3, H, W] RGB in 0..1 (H, W multiples of 8)
       mask:  float32 [1, 1, H, W], 1 = fill
Output        float32 [1, 3, H, W] RGB in 0..1 (the original outside the mask)
"""
import sys
import time

import numpy as np
import onnxruntime as ort
import torch

import fft_onnx

src, dst = sys.argv[1], sys.argv[2]
fft_onnx.register()
model = torch.jit.load(src, map_location="cpu").eval()
img = torch.rand(1, 3, 256, 256)
mask = (torch.rand(1, 1, 256, 256) > 0.7).float()
torch.onnx.export(
    model, (img, mask), dst, opset_version=fft_onnx.OPSET, input_names=["image", "mask"], output_names=["output"],
    dynamic_axes={"image": {2: "h", 3: "w"}, "mask": {2: "h", 3: "w"}, "output": {2: "h", 3: "w"}}, dynamo=False,
)
print("exported", dst, flush=True)

if "--no-check" in sys.argv:
    sys.exit(0)
sess = ort.InferenceSession(dst, providers=["CPUExecutionProvider"])
rng = np.random.default_rng(0)
worst = 0.0
for h, w in [(512, 512), (384, 640)]:
    yy, xx = np.mgrid[0:h, 0:w]
    x = (0.5 + 0.3 * np.sin(xx / 17.0) * np.cos(yy / 23.0) + 0.05 * rng.standard_normal((h, w)))
    x = np.clip(np.stack([x, x * 0.9, x * 0.8]), 0, 1).astype(np.float32)[None]
    m = np.zeros((1, 1, h, w), np.float32)
    m[..., h // 3: h // 3 + h // 4, w // 4: w // 4 + w // 3] = 1
    with torch.no_grad():
        want = model(torch.from_numpy(x), torch.from_numpy(m)).numpy()
    t = time.time()
    got = sess.run(None, {"image": x, "mask": m})[0]
    err = float(np.abs(got - want).max())
    worst = max(worst, err)
    print(f"{h}x{w}: ORT {time.time() - t:.1f}s, max |onnx - torch| = {err:.2e}", flush=True)
assert worst < 2e-3, worst
print("OK")
