import numpy as np
import matplotlib.pyplot as plt

level = [-15,-14,-13,-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]
mean  = [0.6,0.8,1.2,1.0,1.1,0.75,0.6,0.9,0.87,1.26,1.35,1.3,1.27,1.25,1.15,0.83,0.71,0.55,0.73,1.23,0.92,0.95,1.1,1.03,1.01,1.22,1.24,1.23,1.3,1.32,1.47]
low   = [0,0.01,0.19,0,0.19,0,0.01,0.1,0,0.03,0.09,0.09,0.05,0.09,0.02,0,0.1,0.07,0.23,0.02,0,0.07,0.17,0.27,0,0.1,0.02,0.9,0.16,0.11,0.56]
high  = [1.63,1.78,2.15,2.34,1.86,2.02,1.75,1.91,2.48,2.92,2.77,2.61,3.12,3.66,3.68,2.33,1.47,1.35,1.5,4.25,1.96,2.14,2.59,3,3.02,3.34,3.18,2.9,3.06,3.66,2.7]

plt.figure(figsize=(9, 5), dpi=150)
plt.fill_between(level, low, high, color='#8ec6e6', alpha=0.4, label='Range')
plt.plot(level, mean, 'o-', color='#1f4e9c', markersize=5, linewidth=1.8, label='Mean')
plt.xlabel('Liquid Level (mm)'); plt.ylabel('Error (mm)')
plt.ylim(0, 5); plt.grid(True, linestyle='--', alpha=0.4)
plt.legend(loc='upper right'); plt.tight_layout()
plt.show()