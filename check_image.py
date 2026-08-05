from PIL import Image
import numpy as np

img = Image.open('/Users/sandhiya/Documents/iOSLauncher/screen_blank.png').convert('RGB')
arr = np.array(img)
print(f"Image shape: {arr.shape}")
print(f"Unique colors: {len(np.unique(arr.reshape(-1, arr.shape[2]), axis=0))}")
print(f"Mean RGB: {arr.mean(axis=(0,1))}")
