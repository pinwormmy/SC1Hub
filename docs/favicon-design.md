# SC1Hub favicon

기존 원형 배지·미네랄·숫자 `1`을 유지한 흑백 아이콘. 검정 원 안에 흰색 미네랄과 검정 숫자를 배치했으며, 원 바깥은 불투명한 흰색이다. GitHub 파비콘의 원형 배지와 굵은 실루엣 구성을 참고했다.

- 제작: built-in image generation 편집, Pillow로 크기 변환 및 ICO 인코딩
- PNG: `src/main/resources/static/images/sc1hub-icon.png` (512×512)
- ICO: `src/main/resources/static/favicon.ico` (16, 32, 48, 64, 128, 256px)
- 연결: `src/main/webapp/WEB-INF/views/include/head.jspf`
- 캐시 갱신: 기존 `applicationScope.assetVersion` 사용

## Final edit prompt

Edit the attached existing SC1Hub favicon. COLOR CHANGE ONLY. Preserve the EXACT circular badge shape, mineral crystal silhouette, large numeral 1, geometry, positions, proportions, white exterior corners and framing. Replace every dark navy area with pure black #000000. Replace every cyan crystal area with pure white #FFFFFF. Keep the numeral 1 pure black. Keep the background outside the circle solid opaque white #FFFFFF. The final icon is a pure BLACK CIRCLE containing a WHITE MINERAL CLUSTER with a BLACK numeral 1. Only black and white, with neutral gray allowed solely for edge antialiasing. Flat solid fills, no gradients or shading, no colored pixels, no texture, no redesign, no new elements, no checkerboard. Output one finished square icon.

## Verification

ICO의 여섯 프레임을 다시 열어 크기를 확인하고, 16~64px 아이콘을 밝은 배경과 남색 배경에서 확인했다. 원 바깥은 불투명한 흰색이므로 어두운 탭에서 흰 모서리가 보인다.
