# SMS Forwarder for Android

Kotlin으로 작성한 Android 앱입니다. 수신 SMS를 전달하는 기능을 구현합니다.

## 빌드와 실행

Android Studio에서 저장소를 열고 Android SDK와 JDK 21을 설정한 뒤 앱을 실행하세요. Gradle Wrapper로 디버그 APK를 빌드할 수 있습니다.

```sh
./gradlew :app:assembleDebug
```

앱은 SMS 수신과 발신 권한을 요청합니다. 실제 송수신은 통신 가능한 Android 기기와 회선에서 확인해야 합니다. 에뮬레이터 실행만으로 실제 SMS 송수신이 검증된 것은 아닙니다.

## 상태

Android 앱 프로젝트입니다. 서명 키, 전화번호, 토큰과 같은 비밀·개인정보는 저장소에 넣지 마세요.
