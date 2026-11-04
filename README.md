# OnePlus 13T (pagani) OReality Audio / Holo renderer

ColorOS 17 pagani의 AudioX 엔진과 OPlus spatializer를 PixelOS Android 17에
등록하는 vendor 트리입니다. 소스 제어 앱은 OReality 프로파일·10밴드 EQ와
AOSP Spatializer를 통한 공간화 설정을 제공합니다.

**이 UI 변경본의 ROM 빌드·실기기 확인은 미완료입니다.** Holo의 앱별 위치 지정은
포함하지 않습니다. 공간화 가능 여부는 AOSP의 재생 형식·출력 장치 정책에 따릅니다.

OReality 화면은 13T 스톡의 신형 **스마트·몰입·고음질·사용자 지정** 4모드와
원본 카드 이미지로 재구현했습니다. EQ는 원본 8개 프리셋과 ±6 dB 세로형
10밴드이며, 사용자 지정 카드 오른쪽 아래 버튼으로 엽니다. 소리 및 진동 목록의
설명도 이 구성에 맞췄습니다. COUI/스톡 Settings APK 전체를 실행하는 방식이 아닌
AOSP 네이티브 UI 구현이므로 글꼴·블러 등 세부 렌더링은 ColorOS와 다를 수 있습니다.

스마트 모드는 스톡 AudioEffectCenter의 앱 분류 목록과 AOSP 재생 콜백을 사용합니다.
OPlus 전용 앱 감지·온라인 분류 업데이트 서비스는 포함하지 않으며, 미등록 앱은
오디오 속성과 앱 카테고리로 분류합니다. 빠른 설정의 OReality 타일은 포트에 추가한
기능으로, 새 빌드 설치 후 타일 편집 화면에서 직접 추가할 수 있습니다.

## 통합

ROM 루트에서 저장소를 `vendor/oneplus/oreality-pagani`에 배치합니다.
pagani `device.mk`의 공통/기기 vendor 트리 상속 다음에 추가합니다.

```make
TARGET_ENABLE_ONEPlus_OREALITY ?= true
ifeq ($(TARGET_ENABLE_ONEPlus_OREALITY),true)
$(call inherit-product, vendor/oneplus/oreality-pagani/oreality.mk)
endif
```

**현행화된 pagani의 proprietary-files.txt로 `vendor/oneplus/pagani`를 재생성한 뒤
함께 사용해야 합니다.** 기존 vendor 캐시는 공유 엔진 모듈이 없을 수 있습니다.
이 저장소는 `liboplus_audiox`, `libOplusSpatializerAidl`을 정의하지 않고
`vendor/oneplus/pagani` namespace에서 가져옵니다. 공간 음향 튜닝 20개도
pagani가 설치하며, OReality는 AudioX 튜닝 47개와 추가 스피커 룸 모델 2개만 복사합니다.

기존 디바이스 커밋 `d84ab49`에 이 통합이 들어 있습니다. 별도의 BoardConfig
상속은 필요하지 않습니다. 공통 트리 수정 없이 `/odm/etc/audio_effects_config.xml`로
기존 `/vendor/etc/audio/sku_sun/audio_effects_config.xml`을 우선합니다.
공통 트리의 기존 효과·처리 체인을 유지한 XML에 AudioX/spatializer만 추가했습니다.

```sh
python3 vendor/oneplus/oreality-pagani/extract-files.py /path/to/pagani-dump
python3 vendor/oneplus/oreality-pagani/tools/verify-port.py --rom-root "$PWD" \
  --pagani-device-tree device/oneplus/pagani \
  --pagani-vendor-tree vendor/oneplus/pagani
python3 vendor/oneplus/oreality-pagani/tools/tests/test_extract.py
```

추출기는 72개 입력을 모두 검사한 뒤 복사합니다. 고정 해시 불일치·누락은 실패하며
기존 blob을 교체하지 않습니다. `--write-sums`는 새 항목의 해시만 추가합니다.
검증에는 Python 3.10+, readelf, JDK(javac/java)가 필요합니다.

## 설치 구성

| 구성 | 경로 |
| --- | --- |
| 엔진 | `/odm/lib64/liboplus_audiox.so` |
| 효과 | `/odm/lib64/soundfx/libOplusAudioxAidl.so`, `libOplusSpatializerAidl.so` |
| AudioX 튜닝 47개 | `/odm/etc/audio/audiox_param/` |
| Spatializer 튜닝 22개 | `/odm/etc/spatializer/` |
| HAL 설정 | `/odm/etc/audio_effects_config.xml` |
| 제어 앱 / 권한 목록 | `/system/priv-app/ORealityControl`, `/system/etc/permissions/privapp-permissions-oreality.xml` |

`liboplus_audiox`, `libOplusSpatializerAidl`, `libMNN`, `libc++_shared`와
공간 음향 튜닝 20개는 pagani vendor 제공본을 사용합니다.
설치 구성 표의 나머지 항목과 추가 스피커 룸 모델 2개는 OReality가 제공합니다.
72개 원본·고정 해시 목록은 추출/검증용 스냅샷으로 유지하며 모두 설치하지는 않습니다.
`libOplusSpatializerAidl`에 pagani 추출기가 적용하는 `libbase.so` 의존성 추가도
생성 결과 검증에서 허용합니다.
엔진 속성은 `PRODUCT_VENDOR_PROPERTIES`, 플랫폼 공간화 활성화 속성은
`PRODUCT_SYSTEM_PROPERTIES`로 설정합니다. 지원하지 않는 MetaAudio 라우팅 기능을
광고하는 속성은 추가하지 않습니다.

AudioX의 **타입과 구현 UUID는 모두** `41f6c0f4-5d8f-11ec-bf63-0242ac130002`입니다.
XML에는 이 타입을 명시합니다. 앱 생성자의 `EFFECT_TYPE_NULL`은 구현 UUID로
선택하기 위한 AOSP 값이며 엔진의 descriptor 타입이 아닙니다.
Spatializer 구현 UUID는 `de4d6aaf-4e9d-473d-8168-4ce92d50a637`이고,
HAL은 XML 이름 `spatializer`로 표준 타입을 매핑합니다.

Settings의 소리 및 진동에 두 항목을 넣습니다. persistent platform 앱이 AudioX를
소유하고, spatializer 인스턴스는 프레임워크가 소유합니다. 서비스는 서버 상태
콜백으로 AudioX를 다시 만들고, 일시적인 생성 실패는 최대 5회 재시도합니다.
음량·장치·회전 및 공간화 출력 변경을 반영합니다. 화면은 비동기 서비스 연결과
상태 알림을 통해 갱신됩니다.

앱의 모드 ID, 스톡의 재생 프로파일 ID, AudioX 슬롯은 서로 다릅니다.
`StockAudioModes.java`가 스톡 신형 모드와 실제 프로파일을 변환하고,
`EffectProtocol.java`는 EQ의 millibel → dB Q22 변환을 담당합니다.
EQ 설정과 마지막 사용자 지정 값은 스피커/이어폰별로 보존합니다. 기존 음악·영화·게임
선택은 각각 고음질·몰입·스마트로 이전하며 기존 데이터 삭제는 필요하지 않습니다.

[스톡 UI 구현 근거와 검증 범위](docs/stock-ui.md)에 원본 파일, 프리셋 값, 호환성 및
실기기에서 확인할 항목을 기록했습니다. 원본 그림과 분류 목록의 재현/검증은 다음으로 합니다.

```sh
python3 tools/import-stock-ui.py /path/to/pagani-dump
python3 tools/import-stock-ui.py --verify-only
```

검토 근거, 검사 결과와 실기기 확인 항목은 [docs/validation.md](docs/validation.md)를 참고하세요.
