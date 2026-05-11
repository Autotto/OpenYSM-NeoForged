<div align="center">
  <img src="images/brand.png" alt="logo" width="300"/>
  <h1>OpenYSM-Updated</h1>
  <p>OpenYSM 的 fabric 高版本移植</p>
</div>

# 注意
该项目仅用于测试高版本移植可行性，对可用性没有任何保证，如果发现问题请提交 issue。  
面剔除坏了暂时没修  

## 版本支持

| 版本      | 支持状态                                        |
|---------|---------------------------------------------|
| 1.20.1  | ✅ (原生)                                      |
| 1.21.1  | ✅ (见 commit history)                        |
| 1.21.4  | ✅ (见 commit history, 有的类忘了交了可能无法编译，从后面的提交里找 |
| 1.21.8  | ✅ (见 commit history)                        |
| 1.21.9  | ✅ (见 commit history)                        |
| 1.21.10 | ✅ (见 commit history)                        |
| 1.21.11 | ✅                                           |

## 已知问题
ModelPreviewRenderer 中的某些动画暂未适配, 会出现问题 (sleep, ride 等动画)
