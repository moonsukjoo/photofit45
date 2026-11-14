# 포토핏 (PhotoFit) - Android 사진 편집 & 대량 용량 압축 앱

휴대폰 속 사진의 용량 압축, 크기/해상도 조절, 포맷 변환(WEBP/JPEG/PNG), 감성 필터 보정, 워터마크 각인, EXIF 메타데이터 조회/삭제 및 PDF 내보내기를 지원하는 **Kotlin + Jetpack Compose (Material 3)** 기반 Android 애플리케이션입니다.

---

## 주요 기능

- **단일 사진 정밀 편집**
  - 화질(%) 조절 및 목표 용량(KB) 맞춤 압축
  - 비율 축소(75% / 50% / 25%), FHD/HD, 소셜 규격(인스타 1080px, 카카오 640px, 웹 500px), 직접 크기 지정, 90° 회전/좌우 반전
  - 포맷 변환 (`WEBP`, `JPEG`, `PNG`)
  - 9종 감성 필터 및 미세 보정(밝기, 대비, 채도, 색온도, 비네트) + 길게 눌러 원본 비교
  - 실시간 워터마크 서명 & EXIF 상세 메타데이터 조회(GPS 촬영 위치 지도 확인 포함)
- **일괄 사진 압축 및 정리**
  - 사진 다중 선택 및 **전체 선택 / 전체 해제 / 선택 제거**
  - **스마트 파일명 정리**: EXIF 촬영 날짜_시간 순번 또는 사용자 지정 접두사로 일괄 이름 변경
  - **개인정보 EXIF 제거**: SNS 업로드 시 GPS 위치 및 기기 정보 제거
  - **일괄 워터마크** 및 **A4 PDF 묶기** 내보내기
  - Android Scoped Storage(`MediaStore.createDeleteRequest`) 기반 안전한 원본 삭제 지원
- **보관함 & 저장공간 절약 대시보드**
  - 총 절약한 용량 통계 요약, 결과물 갤러리 조회, 원클릭 공유

---

## GitHub에 올리고 자동 빌드(APK 생성)하기

이 프로젝트는 GitHub에 푸시하면 **GitHub Actions**가 자동으로 단위 테스트와 APK 빌드를 수행하도록 설정되어 있습니다(`.github/workflows/android.yml`).

### 1. GitHub 저장소에 업로드
```bash
git init
git add .
git commit -m "Initial commit: PhotoFit Android app"
git branch -M main
git remote add origin https://github.com/<내계정>/<저장소이름>.git
git push -u origin main
```

### 2. GitHub에서 빌드된 APK 다운로드하기 (Android Studio 불필요)
1. GitHub 저장소 상단의 **Actions** 탭으로 이동합니다.
2. **Android CI & APK Build** 워크플로우 실행이 완료되면 클릭합니다.
3. 하단 **Artifacts** 영역에서 **`PhotoFit-Debug-APK`** 를 클릭해 다운로드하고, 압축을 푼 `app-debug.apk`를 Android 휴대폰에 설치합니다.

---

## 로컬 PC(Android Studio / CLI)에서 직접 빌드하기

### 필수 환경
- **JDK 21**
- **Android Studio** (Meerkat 이상 권장) 또는 Android SDK (API 36)

### CLI 빌드 명령어
```bash
# macOS / Linux
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew assembleDebug

# Windows (PowerShell / CMD)
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```
> **참고**: 클론 직후 `debug.keystore`가 없더라도 Gradle이 자동으로 `debug.keystore.base64`를 복원하여 서명 빌드를 수행합니다.

생성된 APK 경로:
- `app/build/outputs/apk/debug/app-debug.apk`

---

## (선택) Release 서명 APK / AAB 자동 빌드 설정

스토어 배포용 Release APK/AAB를 GitHub Actions에서 함께 빌드하려면, GitHub 저장소의 **Settings → Secrets and variables → Actions**에 아래 3개 Secret을 등록하세요.

| Secret 이름 | 설명 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | 서명용 `.jks` 파일을 Base64로 인코딩한 문자열 (`base64 -w 0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore 비밀번호 |
| `KEY_PASSWORD` | `upload` 키 별칭(alias)의 비밀번호 |

등록 후 `v1.0.0` 형태의 태그를 푸시하거나 Actions 탭에서 **Run workflow**를 실행하면 `PhotoFit-Release-Artifacts`가 함께 생성됩니다.
