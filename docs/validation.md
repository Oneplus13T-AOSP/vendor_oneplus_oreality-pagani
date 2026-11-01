# 재검토 및 검증 기록

## 검토 범위

- vendor 이력: `0f5a2bc`, `f1e037c` 및 현재 전체 소스.
- device 이력: PixelOS 원본의 `d84ab49` 등 최근 커밋과 예비 트리 전체 파일 비교.
  예비 트리는 원본과 내용이 같지만 `.git` 상대 링크가 끊어져 있습니다.
  이 검토는 원본 device 저장소의 Git 메타데이터를 읽었으며 링크는 변경하지 않았습니다.
- `/home/ccc/dumpyara/working/cos`의 원본 APK, ELF, 효과 XML, 튜닝 데이터.
- PixelOS의 Soong, AudioManager, Spatializer, AudioService, Qualcomm EffectConfig,
  audio_config.h, sm8750-common 오디오 설정, pagani vendor 의존 라이브러리.
- 참고 포트: `vendor/oplus/camera`, `vendor/xiaomi/apps-nabu`,
  `vendor/xiaomi/dolby-nabu`, 예비 renoir의 `dolby-renoir`, `miuicamera-renoir`.
  앱/권한 파티션, 제품 상속, 서명 및 Dolby 서비스의 서버 상태 콜백을 참고했습니다.

## 발견 및 수정

1. `prebuilt_etc`에 존재하지 않는 `system: true` 속성을 제거했습니다.
   `/system`이 기본 파티션입니다. bpfmt는 문법 포매터이므로 이 속성의 유효성을
   보증하지 않습니다.
2. 권한 모듈 이름은 `.xml`이 붙어 있었지만 PRODUCT_PACKAGES는 확장자 없는
   이름을 요청했습니다. 모듈명을 일치시키고 설치 파일명을 별도로 고정했습니다.
3. Manifest의 `.Activity`/`.Service`/`.BootReceiver`는 패키지
   `com.oplus.oreality.audio`로 확장되지만 Java 클래스는 `org.lineageos.oreality`에
   있었습니다. 모든 컴포넌트를 완전한 클래스명으로 선언했습니다.
4. 서비스의 `getDisplay()`를 DisplayManager의 기본 디스플레이 조회로 교체했습니다.
   서비스는 화면 컨텍스트가 아닙니다.
5. AUDIO_BECOMING_NOISY는 audioserver 생명주기 알림이 아닙니다.
   AudioServerStateCallback으로 교체하고 실패 재시도, stale handle 처리,
   START_STICKY, 콜백 해제를 추가했습니다.
6. 음량·오디오 장치·디스플레이·spatializer 상태/출력 변경을 관찰합니다.
   재생이 시작된 뒤 만들어지는 spatializer에도 파라미터를 다시 전달합니다.
   프로파일도 부팅 및 서버 복구 시 재적용합니다.
7. startService 직후 서비스가 동기적으로 생성된다고 가정하던 화면을
   ServiceConnection 기반으로 바꾸고 서비스 상태 변경 시 갱신합니다.
   EQ 화면은 서비스 생성 전에도 저장값을 읽고 보존합니다.
8. 스톡 c0.a(AudioxEffect)의 변환을 재확인했습니다. **스톡 음악 profile=2는
   AudioX 슬롯 1**, 영화 profile=1은 슬롯 2입니다. 이전 커밋은 앱 저장 ID를
   스톡 ID로 잘못 해석했습니다. 앱 저장 ID는 유지하고 명시적으로 변환합니다.
   Holo에는 AudioX 슬롯이 아니라 스톡 profile ID(0..5)를 보냅니다.
9. millibel 값에 곧바로 2^22를 곱하면 100배 커지고 ±12 dB UI 범위의 상당 부분이
   int 상한/하한으로 포화됩니다. dB로 변환한 후 Q22를 적용합니다.
   ±12 dB는 ±50,331,648이며 손상된 저장값은 범위를 제한합니다.
10. AudioX 타입 UUID를 구현 UUID와 일치시켰습니다. 앱 선택자의 NULL UUID와
    descriptor의 실제 타입을 구분합니다. ELF에서 타입 초기화가 참조하는 문자열은
    `0x93cf6`, 구현 초기화 문자열은 `0x93cd1`이며 둘 다
    `41f6c0f4-5d8f-11ec-bf63-0242ac130002`입니다.
    타입 guard GOT relocation은 `0x176720`입니다.
11. 미구현 앱별 배치 기능을 광고하던 속성과 UI 설명을 수정했습니다.
    자동 장면 분류 서비스가 없는 기본 프로파일을 Smart라고 부르지 않습니다.
12. 추출기가 해시 불일치를 경고만 하고 성공 처리하던 문제를 고쳤습니다.
    모든 입력을 먼저 검사하고 잘못된 덤프로 기존 blob을 덮어쓰지 않습니다.
13. 검증기를 보강했습니다. Manifest 클래스 존재, 실제 요청되는 권한 모듈,
    Soong 파티션 속성, 필수 ODM 제공본, 호스트 프로파일/EQ 동작을 검사합니다.
    기존의 코드 문자열 존재 검사와 실제 동작 검증을 구분합니다.

## 프로파일 변환 근거

스톡 APK `AudioEffectCenter.apk`의 `c0.a.g(int)` 및 `d0.h.m(int)`를 대조했습니다.
앱 저장 ID는 기존 버전 호환을 위해 유지합니다. 스톡 UI 카테고리와 스톡의
장면 분류 프로파일은 별개이며 이 포트는 스톡 장면 분류 서비스를 포함하지 않습니다.

| 앱 선택 | 저장 ID | 스톡 프로파일 / Holo | AudioX 슬롯 |
| --- | --- | --- | --- |
| 기본 | 0 | 0 | 6 |
| 음악 | 1 | 2 | 1 |
| 영화 | 2 | 1 | 2 |
| 게임 | 3 | 4 | 4 |
| 몰입 | 4 | 3 | 3 |
| 선명 | 5 | 5 | 5 |
| 사용자 지정 | 6 | 0 | 6 |

스톡 AudioxService는 EQ를 Q22로 바꾼 뒤 c0.a.e에 전달합니다.
포트 UI 저장 단위는 millibel이므로 먼저 100으로 나눕니다.
표의 파라미터 전달은 정적으로 확인했으며 실제 각 슬롯의 음향은 청취 확인이 필요합니다.

## 수행한 검증

- 72개 blob의 고정 SHA-256 및 제공된 스톡 덤프와의 바이트 일치 확인.
- 효과 XML 파싱, UUID/라이브러리 참조, 기존 Qualcomm 효과·처리 체인 유지 확인.
- 효과 3개 ELF의 DT_NEEDED, pagani libMNN/libc++_shared 제공 확인.
  **런타임 링커 네임스페이스와 전체 ABI 폐포를 보증하는 검사는 아닙니다.**
- Android 17 `android_system_server_stubs_current.jar` 대상 전체 앱 Java 컴파일.
- 현재 ROM의 framework-res.apk를 사용한 aapt2 리소스 컴파일·링크.
- bpfmt, git diff --check.
- verify-port.py의 정적 검사와 프로파일/Q22 호스트 동작 테스트.
- 추출기 회귀 테스트 4개: 정상 추출, 누락, 해시 불일치,
  --write-sums가 기존 고정 해시를 바꾸지 않는지 확인.

`out/target/product/pagani`가 없어 실제 설치 결과 검사는 경고입니다.
**Soong 전체 모듈 그래프 생성, ROM 빌드, 부팅, SELinux enforcing에서 dlopen,
실제 이펙트 생성·음향 변화는 검증하지 않았습니다.**

## 빌드 / 실기기 확인

ROM 빌드 후 verify-port.py를 다시 실행하고 다음을 확인합니다.

```sh
adb shell ls /odm/etc/audio_effects_config.xml /system/etc/permissions/privapp-permissions-oreality.xml
adb shell getprop ro.audio.spatializer_enabled
adb shell dumpsys package com.oplus.oreality.audio
adb shell dumpsys activity services com.oplus.oreality.audio
adb shell dumpsys media.audio_flinger
adb shell dumpsys media.audio_policy
adb shell dumpsys audio
adb logcat -d -s ORealityService AndroidRuntime linker
```

Settings 진입, 콜드 부팅 및 잠금 해제 전 복원, audioserver 복구,
프로파일/EQ 청취 비교, 음량 변경에 따른 튜닝, 회전·장치 전환을 확인해야 합니다.
스피커/유선/블루투스 재생, 공간화 가능한 다채널 콘텐츠, 통화·알림·벨소리,
사용자 전환에서도 확인해야 합니다. 성공 여부를 UI 스위치만으로 판단하지 않습니다.

앱별 Holo 위치 지정에는 atlasservice/MetaAudio 및 OPlus AudioFlinger 확장이
추가로 필요합니다. 해당 기능은 구현하지 않았습니다. Direct/Compress 경로의
효과 적용 여부, 기본/몰입/선명 프로파일의 스톡 동등성, Bluetooth 전용 파라미터,
CPU 부하는 실기기 검증 과제로 남습니다.
