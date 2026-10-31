# OReality Audio + Holo Audio for OnePlus 13T (pagani)

OnePlus 13T ColorOS 17(중국판)의 **OReality Audio**와 **Holo Audio**를 PixelOS Android 17에
이식하는 vendor 저장소입니다. 스톡 덤프(ColorOS 17, pagani)의 원본 바이너리만
사용하며, 뮤직/무비/게임 프로파일과 스피커·헤드폰 튜닝은 13T 원본을 그대로 씁니다.

## 무엇이 들어가는가

| 구성 | 원본 | 설치 경로 |
| --- | --- | --- |
| AudioX 엔진 | `odm/lib64/liboplus_audiox.so` | `/odm/lib64/` |
| AudioX 이펙트 | `odm/lib64/soundfx/libOplusAudioxAidl.so` | `/odm/lib64/soundfx/` |
| Spatializer 이펙트 | `odm/lib64/soundfx/libOplusSpatializerAidl.so` | `/odm/lib64/soundfx/` |
| AudioX 파라미터 47개 | `odm/etc/audio/audiox_param/*.bin` | `/odm/etc/audio/audiox_param/` |
| Spatializer 파라미터 22개 | `odm/etc/spatializer/*.bin` | `/odm/etc/spatializer/` |
| 제어 앱 | 이 저장소 소스 | `/system/priv-app/ORealityControl` |

`liboplus_audiox.so`는 `libMNN`과 `libc++_shared`를 요구하며, 두 라이브러리는
이미 pagani vendor blob 트리가 `/odm/lib64`에 설치합니다. 중복 설치하지 않습니다.

## 엔진 등록

Qualcomm AIDL Effects HAL(`libaudioeffecthal.qti`)은
`audio_find_readable_configuration_file("audio_effects_config.xml")`로 설정을 찾습니다.
검색 순서가 `/odm/etc`, `/vendor/etc/audio/sku_<sku>`, `/vendor/etc`, `/system/etc`이므로
`ro.boot.product.vendor.sku=sun`인 pagani에서는 기존
`/vendor/etc/audio/sku_sun/audio_effects_config.xml`이 그대로 선택됩니다.
이 저장소는 **그 파일을 OReality/Holo 항목을 추가한 사본으로 교체**합니다.
새 파일을 다른 경로에 두면 HAL이 읽지 않습니다.

```xml
<library name="oplus_audiox_sw" path="libOplusAudioxAidl.so"/>
<library name="spatializersw"    path="libOplusSpatializerAidl.so"/>

<effect name="oplusaudiox" library="oplus_audiox_sw"
        uuid="41f6c0f4-5d8f-11ec-bf63-0242ac130002"
        type="ec7178ec-e5e1-4432-a3f4-4657e6795210"/>
<effect name="spatializer" library="spatializersw"
        uuid="de4d6aaf-4e9d-473d-8168-4ce92d50a637"/>
```

두 UUID는 스톡 라이브러리에서 직접 확인했습니다.

- `41f6c0f4-5d8f-11ec-bf63-0242ac130002` — `libOplusAudioxAidl.so`의 `AudioEffect`
  구현 UUID (`c0.a`가 `EFFECT_TYPE_NULL`로 여는 값과 일치).
- `ec7178ec-e5e1-4432-a3f4-4657e6795210` — AOSP `kEffectUuidNull`. AudioX는
  표준 타입이 없는 vendor 이펙트이므로 타입을 비웁니다.
- `de4d6aaf-4e9d-473d-8168-4ce92d50a637` — `libOplusSpatializerAidl.so`의 구현 UUID.
  이름을 `spatializer`로 두면 HAL이 `FX_IID_SPATIALIZER`를 타입으로 매핑하므로
  프레임워크 `Spatializer` API가 이 인스턴스를 소유합니다.

## 설정 진입점

`ORealityControl` 하나가 두 항목을 노출합니다.

- **OReality Audio** — `settings.category.ia.sound`, order `-95`
- **Holo Audio** — 같은 카테고리, order `-94`

둘 다 PixelOS 설정의 **소리 및 진동** 안에서 Dolby Atmos가 있던 자리와 그 아래에
표시됩니다. 앱은 persistent platform app으로 두 이펙트를 직접 소유합니다.

## 빌드

```sh
# 저장소 배치
ln -s /path/to/vendor_oneplus_oreality-pagani vendor/oneplus/oreality-pagani

# 원본 추출 (해시 검증 포함)
python3 vendor/oneplus/oreality-pagani/extract-files.py /path/to/pagani-dump

# 검증
python3 vendor/oneplus/oreality-pagani/tools/verify-port.py --rom-root /home/ccc/android/pixelos
```

pagani `device.mk`에서 **공통 트리를 상속한 뒤** 다음을 추가합니다. 공통 트리보다
나중에 상속해야 `TARGET_ENABLE_ONEPlus_OREALITY` 게이트가 정상 동작하고, 속성
오버라이드도 최종 값으로 남습니다.

```make
TARGET_ENABLE_ONEPlus_OREALITY ?= true
ifeq ($(TARGET_ENABLE_ONEPlus_OREALITY),true)
$(call inherit-product, vendor/oneplus/oreality-pagani/oreality.mk)
endif
```

`audio_effects_config.xml`은 `/odm/etc`에 설치됩니다. HAL의 검색 순서가
`/odm/etc`를 먼저 보므로 `sm8750-common`이 설치하는
`/vendor/etc/audio/sku_sun/audio_effects_config.xml`은 그대로 두어도 선택되지
않습니다. **공통 트리를 수정할 필요가 없습니다.**

## 검증 상태

- 원본 SHA-256 고정 및 재추출 검증: `proprietary-files.txt`
- ELF 의존성 폐포 검사: `tools/verify-port.py`
- XML 스키마/중복 UUID 검사: `tools/verify-port.py`
- **전체 ROM 빌드·부팅·실기기 음향 검증은 아직 하지 않았습니다.**

실기기에서 확인해야 하는 항목은 `docs/validation.md`에 정리했습니다.
