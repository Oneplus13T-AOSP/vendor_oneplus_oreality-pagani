# 검증 범위와 한계

이 문서는 **실제로 확인한 것**과 **실기기에서만 확인할 수 있는 것**을 구분합니다.
"통과했다"는 표현은 아래 표의 정적 검사에만 해당합니다.

## 정적 검사 (수행 완료)

`python3 tools/verify-port.py --rom-root /home/ccc/android/pixelos`

| 검사 | 내용 | 결과 |
| --- | --- | --- |
| 원본 고정 | 72개 파일 SHA-256 대조 | 통과 |
| 효과 XML | well-formed, UUID 중복 없음, 라이브러리 참조 유효 | 통과 |
| 효과 UUID | 바이너리가 실제로 보고하는 값과 일치 | 통과 |
| 의존성 폐포 | 3개 라이브러리의 DT_NEEDED가 모두 제공됨 | 통과 |
| HAL 설정 | `/odm/etc` 사본 설치 + 검색 순서 1순위 | 빌드 후 확인 |
| 권한 allowlist | 선언한 특권 권한 전부 허용 | 통과 |
| 속성 | `ro.oplus.audio.effect.type`, `ro.audio.spatializer_enabled` | 통과 |

추가로 확인한 사실:

- `liboplus_audiox.so`의 DT_NEEDED는 `libc`, `libdl`, `libm`, `liblog`,
  `libMNN`뿐입니다. `libMNN`과 `libc++_shared`는 pagani vendor 트리가 이미
  `/odm/lib64`에 설치하므로 중복 설치하지 않습니다.
- `libOplusAudioxAidl.so`와 `libOplusSpatializerAidl.so`는 둘 다
  `liboplus_audiox.so`를 요구하며, 오디오 알고리즘 심볼
  (`oplus_algo_spat_*`, `oplus_algo_audiox_*`)은 그 안에 정의되어 있습니다.
  `system_ext`의 `libspatializerRender.so`는 필요하지 않습니다.
- 두 이펙트 라이브러리 모두 `createEffect`/`queryEffect`/`destroyEffect`를
  내보내고 `EffectImpl`/`BnEffect`/`IFactory` 심볼을 자체 보유하므로,
  AIDL 효과 HAL이 `dlopen` 후 심볼을 찾는 방식과 맞습니다.
- 두 라이브러리 모두 `getInterfaceVersion`이 **2**를 보고합니다. 프레임워크의
  `kReopenSupportedVersion`이 2이므로 `reopen()` 경로를 사용합니다.
- 이펙트 HAL이 요구하는 NDK 라이브러리(`android.hardware.audio.effect-V2-ndk`,
  `android.media.audio.common.types-V4-ndk`)는 두 라이브러리의 DT_NEEDED에
  없습니다. 정적 링크가 아니라 심볼을 직접 참조하지 않기 때문이며, HAL 쪽에서
  제공됩니다.

## 빌드 후 확인 (사용자 빌드)

```sh
python3 vendor/oneplus/oreality-pagani/tools/verify-port.py --rom-root /home/ccc/android/pixelos
```

`out/target/product/pagani`가 존재하면 다음을 추가로 검사합니다.

1. `/odm/etc/audio_effects_config.xml`이 실제로 설치됐는지
2. 그 파일에 두 라이브러리가 등록됐는지
3. `audio_config.h`의 검색 순서가 여전히 `/odm/etc`로 시작하는지

`out/target/product/pagani`가 없으면 1~3은 경고로 남고 실패로 처리하지 않습니다.

## 실기기에서만 확인 가능한 것

다음은 정적으로 보장할 수 없습니다.

### OReality

1. `AudioEffect` 인스턴스가 실제로 생성되는지
   ```sh
   adb shell dumpsys media.audio_flinger | grep -i audiox
   adb logcat -d | grep -iE "OplusAudiox|AudioX"
   ```
2. 프로파일 변경이 출력 PCM을 실제로 바꾸는지 (청취 비교)
3. 스피커 음량 구간별 튜닝 표(`SPK_*_lvl1..5`)가 올바로 선택되는지
4. EQ 10밴드 값이 엔진에 적용되는지 (`0x100100BC`, `0x100100C9`)

### Holo Audio

1. 공간화 효과가 `android.media.Spatializer`로 노출되는지
   ```sh
   adb shell dumpsys media.audio_policy | grep -i spatial
   ```
2. `Spatializer.isAvailable()`이 true인지
3. 회전(`0x100100BF`)과 음량(`0x100100BE`) 파라미터 반영
4. 블루투스·유선·스피커 각 경로에서 동작 여부

### 공통

1. 부팅 시 서비스가 두 이펙트를 복원하는지
2. audioserver 재시작 후 복원되는지 (`killall audioserver`)
3. 설정 앱에서 두 항목이 소리 및 진동에 표시되는지
4. 다른 오디오 기능(통화, 알림, 벨소리)에 회귀가 없는지

## 알려진 한계

- **앱별 위치 지정은 구현하지 않았습니다.** Holo Audio의 다중 앱 배치는
  `atlasservice`(`libSpecailizerPLService.so`)와 AudioFlinger 확장
  (`libaudioflingerextimpl.so`, `libaudioprocessingextimpl.so`)이 필요합니다.
  이들은 AOSP `audioserver`의 내부 심볼(`libaudioflinger.so`,
  `libaudioprocessing.so`)에 강하게 결합되어 있어, PixelOS 17 audioserver와
  ABI가 맞는다는 보장이 없습니다. 검증 없이 넣으면 audioserver가 죽습니다.
  그래서 이 포트는 **공간화 렌더러 자체**(Holo Audio의 음향 처리)만 넣고,
  앱별 배치는 UI에서 "사용할 수 없음"으로 표시합니다.
- 오프로드(Direct/Compress) 재생 경로는 소프트웨어 효과 체인을 우회할 수
  있습니다. 이는 AOSP의 일반적인 동작이며 이 포트의 결함이 아닙니다.
- `liboplus_audiox.so`는 `libMNN`(신경망 추론)을 사용합니다. 특정 프로파일의
  장면 감지 경로에서만 호출되는 것으로 보이지만, 실기기 CPU 사용률 확인이
  필요합니다.
