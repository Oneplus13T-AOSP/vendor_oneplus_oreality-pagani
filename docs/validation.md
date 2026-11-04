# 재검토 및 검증 기록

이 문서는 초기 포팅과 UI 개편 이전 검토 기록도 포함합니다. 현재 신형 4모드 UI,
스마트 장면 선택, EQ 프리셋 및 빠른 설정 타일은 [stock-ui.md](stock-ui.md)를 기준으로
보세요. 아래의 이전 실기기 결과가 새 UI의 실기기 검증을 뜻하지는 않습니다.

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

## 추가 의존성·ABI·SELinux·권한 검토

초기 커밋의 검증은 위의 정적 검사 범위이며 **메뉴 실행 오류를 모두 제거했다는
의미가 아닙니다.** 추가로 아래를 확인했습니다.

### 재귀 ELF 및 버전 심볼

```sh
python3 tools/audit-elf.py \
  --rom-root /home/ccc/android/pixelos \
  --platform-image /home/ccc/android/pixelos/out/target/product/nabu \
  --reference-only
```

현재 pagani 출력이 없어 효과 3개와 pagani ODM 제공본(libMNN/libc++_shared),
nabu에 빌드된 같은 PixelOS 플랫폼 라이브러리를 조합했습니다.
재귀 방문 ELF 25개에서 DT_NEEDED 제공본 누락 0개, 강한 동적 심볼 및
명시적 심볼 버전 불일치 0개였습니다. libc IFUNC도 검사에 포함했습니다.
이 결과는 **참고 이미지 기반**이며 pagani 실제 설치/ABI 검증 완료가 아닙니다.

검사기는 플랫폼 제공 경로를 출력하고, 다른 기기 산출물을 사용할 때는
`--reference-only`를 요구합니다. pagani 출력에 대해서 실행하면 저장소의
엔진 대신 실제 이미지의 `/odm/lib64` 엔진과 의존 파일을 검사합니다.
C++ 객체 배치, 심볼 interposition, 실제 링커 네임스페이스, 런타임 dlopen은
이 검사만으로 보장하지 않습니다.

현재 common vendor가 사용하는 `libaudioeffecthal.qti.so`는 prebuilt이고,
DT_NEEDED에 audio.effect-V2-ndk 및 audio.common.types-V3-ndk가 있습니다.
현재 VINTF도 effect HAL version 2를 선언합니다. 효과 라이브러리가 자체 보유한
NDK 인터페이스와 framework 사이의 실제 Binder 연결은 부팅 후 확인해야 합니다.

### 링커 및 SELinux 소스 대조

- Qualcomm 효과 라이브러리 검색 경로에 `/odm/lib64/soundfx`가 있습니다.
  vendor default 네임스페이스는 `/odm/lib64`를 검색하며 `/odm`을 허용합니다.
  효과를 로드하는 프로세스는 앱이 아닌 vendor 오디오 HAL입니다.
- qcom sm8750 file_contexts는 `audiohalservice.qti`를
  `hal_audio_default_exec`로 라벨링합니다.
- 플랫폼 file_contexts에서 ODM 라이브러리는 `vendor_file`,
  `/odm/etc` 튜닝/설정은 `vendor_configs_file`입니다. 확인한 qcom/OPlus
  file_contexts에 이 포트의 해당 경로를 덮어쓰는 규칙은 없습니다.
- 플랫폼 domain 정책은 vendor 도메인의 vendor_file_type 읽기/실행/map과
  vendor_configs_file 읽기를 허용합니다. HAL의 선택적 audio dump 경로에는
  qcom 정책의 vendor_audio_data_file 접근 규칙이 있습니다.
- 앱은 플랫폼 서명 앱 도메인을 사용하고 JNI로 ODM 라이브러리를 직접 로드하지
  않습니다. AOSP 앱 Binder 규칙과 오디오 서비스 API를 사용합니다.

이 소스 대조에서 추가 allow 규칙이 반드시 필요한 접근은 확인되지 않았습니다.
**pagani 정책 컴파일·실제 라벨·enforcing 상태의 AVC 무발생은 검증하지 않았습니다.**
불필요한 광범위 allow나 permissive 도메인은 추가하지 않았습니다.

### 권한과 메뉴 실행

AudioService 구현에서 서버 상태 감시 권한은 MODIFY_AUDIO_ROUTING 또는
MODIFY_PHONE_STATE이며 앱은 전자를 선언/허용합니다. Spatializer의 활성화,
파라미터, 출력 콜백에는 MODIFY_DEFAULT_AUDIO_EFFECTS가 필요하며
선언/allowlist에 있습니다. 앱과 allowlist는 모두 system에 설치되도록 구성했습니다.
Manifest의 Activity/Service/Receiver 클래스는 Java 소스와 일치하고 Android 17
API 대상으로 컴파일되었습니다. 화면에서 서비스 생성 전 접근하는 경로도 수정했습니다.

이는 알려진 클래스 로딩·컨텍스트·서비스 연결 결함과 권한 요건을 정적으로 확인한
것입니다. **설정 메뉴 탭 → 화면 실행 → 효과 생성 → 프로파일/EQ 변경이라는
실제 기기 동작 테스트는 수행하지 않았습니다.** PackageManager의 최종 권한
부여, AndroidRuntime 예외, AudioEffect 생성 실패, SELinux AVC는 실기기 로그로
확인해야 합니다. 따라서 '모든 메뉴 실행 오류를 잡았다'고 판정하지 않습니다.


## 현행화 pagani와의 공유 모듈 통합

현행화된 디바이스 목록은 `liboplus_audiox`, `libOplusSpatializerAidl`과 공간 음향
튜닝 20개를 생성합니다. 이전 포트와 함께 Make에 두 namespace를 공개하면
모듈 중복과 설치 목적지 중복이 생겼습니다. 최초 포팅 커밋부터 OReality의 두
중복 모듈 정의와 튜닝 복사 20개를 제거하는 방식으로 기존 4개 커밋을 리베이스했습니다.
공유 모듈의 PRODUCT_PACKAGES 요청과 Soong namespace import는 유지합니다.
OReality 전용 libOplusAudioxAidl·앱·권한·HAL 설정·AudioX 튜닝 47개 및 추가
스피커 룸 모델 2개도 유지합니다. 스톡 72개 입력과 고정 해시는 변경하지 않았습니다.
디바이스 트리 및 fd1ce01 커밋은 수정하지 않았습니다.

이제 verify-port.py는 `check-integration.py`를 함께 실행합니다.
`--pagani-device-tree`로 실제 사용할 현행화 트리를 지정할 수 있으며 기본값은
`<rom-root>/device/oneplus/pagani`입니다. `--pagani-vendor-tree`도 지정하면
생성된 모듈, 공유 blob 및 튜닝 설치 항목을 확인합니다. `libOplusSpatializerAidl`은
스톡 원본 또는 pagani 추출기의 정확한 `add_needed('libbase.so')` 결과만 허용합니다.

검증 내용:

- 실제 extract-utils의 File/module-name 변환으로 신규 목록과 OReality를 비교:
  공유 모듈 정의 중복 0개, PRODUCT_COPY_FILES 목적지 충돌 0개.
- GNU Make로 true/false 평가: OReality 복사 항목 49개/0개.
  false일 때 OReality의 package/namespace/property 추가도 없음.
- 신규 목록의 공유 라이브러리 4개 및 공간 음향 튜닝 20개만 스톡에서 임시 복사하고,
  spatializer의 libbase.so 보정을 적용한 뒤 실제 extract-utils로 해당 부분의
  Android.bp와 pagani-vendor.mk를 생성해 통합 검증 통과.
  전체 vendor 재생성이나 전체 ROM 빌드를 수행한 것은 아님.
- 수정 전 OReality를 같은 신규 목록에 대조하면 모듈 2개/복사 20개 충돌을 검출.
- 구형 device 목록 또는 구형 vendor 캐시를 지정하면 검증 실패.
- 기존 72개 blob 해시, 효과 XML/권한/Manifest/프로파일/EQ 검증 유지.

**적용 조건:** 실제 빌드 전에 반드시 현행화된 pagani 목록으로 vendor를 재생성해야
합니다. 구형 vendor만 둔 상태에서 이 리베이스본을 단독 적용하면 공유 모듈이
없어 빌드할 수 없습니다. 기존 커밋 해시는 변경되었고 이전 이력은 원본 저장소의
백업 ref로 보존합니다. 전체 Soong/ROM 빌드 및 실기기 검증은 여전히 미수행입니다.
