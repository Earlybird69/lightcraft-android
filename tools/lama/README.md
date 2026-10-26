# AI Remove model (LISTD build)

AI Remove fills spots with [LaMa](https://github.com/advimman/lama) (Apache-2.0), run on the phone
with ONNX Runtime. The app downloads `lama.onnx` once from this repository's `lama-model` release.

The **LISTD model** workflow makes that release: it downloads the big-lama TorchScript checkpoint,
converts it to ONNX with `export_lama.py` and checks the result against PyTorch.

LaMa's Fourier units use 2-D real FFTs, which the ONNX exporter can't translate; `fft_onnx.py`
rebuilds them as matrix products against DFT matrices made in the graph from the input's own
shape, so the model keeps dynamic sizes and runs on any ONNX Runtime build.

Model input: `image` float32 [1, 3, H, W] RGB 0–1 and `mask` float32 [1, 1, H, W] (1 = fill), H and
W multiples of 8; output: float32 [1, 3, H, W] RGB 0–1.
