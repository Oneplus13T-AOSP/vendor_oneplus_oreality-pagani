# 13T 신형 OReality UI 구현

## 사용자에게 보이는 변경

- 스톡 신형의 스마트·몰입·고음질(Fidelity)·사용자 지정 4모드, 원본 이미지와 선택 테두리.
- 사용자 지정 카드 오른쪽 아래 EQ 버튼으로 원본의 프리셋 선택/세로 10밴드 화면 진입.
- 팝·락·일렉트로닉·클래식·보컬 강화·베이스 강화·고음 강화·사용자 지정 8개 선택지.
  여러 프리셋을 동시에 합치는 방식은 아니며 원본처럼 하나를 선택한다.
- 프리셋을 직접 조절하면 사용자 지정으로 전환. 프리셋 선택 전의 마지막 사용자 지정
  값은 따로 보존하며 스피커/이어폰 설정도 분리한다.
- 스톡과 같은 ±6 dB, 1 dB 단위 조절, 초기화 및 접근성/방향키 조절.
- 소리 및 진동 목록의 설명은 “스마트·몰입·고음질 모드와 사용자 지정 이퀄라이저”.
- OReality 빠른 설정 타일. 자동 추가하지 않으며 타일 편집 화면에서 추가한다.
  상태는 실제 효과 핸들에서 읽고, 길게 누르면 OReality 설정으로 진입한다.

COUI에 의존하는 Settings 전체를 설치한 것이 아니다. 원본 카드 이미지와 화면 구조,
EQ 값/선택 순서를 AOSP 네이티브 뷰로 재구현했다. 시스템 글꼴·색상·팝업/블러·
애니메이션은 스톡과 픽셀 단위로 같다고 보증하지 않는다. Holo 화면 자체는 기존 구성을 유지한다.

## 스톡에서 확인한 근거

Settings.apk:
`system_ext/priv-app/Settings/Settings.apk`
SHA-256: `808818365d2beefdbf8bf2af7292b5b7b06ca97ed84ceb08e9d921a0a5dda1c8`

AudioEffectCenter.apk:
`my_product/app/AudioEffectCenter/AudioEffectCenter.apk`
SHA-256: `2186b02458829a89aeabf92c0b043d4a2bdd67534c04955a2cf29427e8954da9`

- `SoundEffectModeAdapter.mOrealitySceneSoundModeListRenewal`: 0/4/5/6 네 모드.
  기존 0/1/2/3 스마트·음악·영화·게임은 별도의 구형 목록이다.
- `SysFeatureUtils.isSoundEffectsRenewal`: `ro.oplus.audio.effect.speaker_geq`.
  이 pagani 덤프는 true다.
- `DolbyModel.EQUALIZER_PRESETS`, `EqualizerPreset`, `eq_type_entries/values`:
  7개 이름 있는 프리셋과 id 0 사용자 지정. 아래 값은 dB이며 전송 전에 100배로
  앱 저장 단위 millibel로 바꾸고 Q22 dB로 변환한다.
- `DolbyEqualizerView`, `dolby_equalizer_min_progress/max_progress`: −6/+6.
  이전 포트의 ±12 dB UI 범위는 원본과 일치하지 않아 수정했다.
- 원본 `oreality_mode_*_renewal.webp` 4개와 AudioEffectCenter의
  `assets/multimedia_dolby_support_apps.xml`은 그대로 추출하고 SHA-256으로 고정했다.

| 프리셋 | 31, 62, 125, 250, 500, 1K, 2K, 4K, 8K, 16K (dB) |
| --- | --- |
| 팝 | 2, 3, 1, −1, 1, 2, 3, 2, 1, 1 |
| 락 | 3, 4, 2, −2, 0, 3, 4, 2, 1, 0 |
| 일렉트로닉 | 5, 5, 3, 0, 1, 2, 3, 4, 3, 2 |
| 클래식 | 1, 1, 0, −1, 0, 2, 3, 2, 0, 1 |
| 보컬 | −3, −2, −1, 2, 3, 4, 5, 3, 2, 1 |
| 베이스 | 6, 5, 5, 3, 1, 0, 0, 0, 0, 0 |
| 고음 | 0, 0, 0, 0, 1, 2, 4, 5, 6, 5 |

스톡의 `DolbyTileService`는 `SysFeatureUtils.isDolbySupported`를 요구한다.
이는 `oplus.software.audio.dolby_support`에 의존하며 13T의 AudioX 전용 구성과
다르다. universal APK 안에 Dolby 타일 클래스가 있다는 사실만으로 13T에서
OReality 타일이 제공된다고 볼 수 없다. 이번 OReality 타일은 별도 포트 기능이다.

## 화면 뒤의 실제 동작

기존 포트의 7개 항목은 구형/신형 모드를 합친 메뉴였다. 이름이 존재한다는 사실이
스톡 신형의 동작을 모두 구현했다는 의미는 아니었다.

신형 모드는 AudioEffectCenter `d0.f.m/p/r`, `c0.a.g`, `d0.h.m`을 대조했다.
이 13T 덤프의 high_quality/gallery_effect 속성은 true가 아니다.

- 스마트: 활성 재생 앱을 원본 분류 목록에 대조한다. 음악→stock profile 2,
  음성→5, 게임→3/4, 동영상→1/11/12 등의 값을 선택한다.
  미등록 앱은 AOSP usage/contentType 및 앱 카테고리를 사용한다.
- 몰입: 스톡의 영화 계열 프로파일. 지원 앱/스피커 회전에 따라 1/11/12를 선택한다.
- 고음질: stock profile 2 → AudioX slot 1. 기존의 voice profile 5 고정 매핑을 수정했다.
- 사용자 지정: stock profile 0 → slot 6 및 선택된 EQ 이득을 적용한다.

재생 정보는 MODIFY_AUDIO_ROUTING 권한이 있는 AOSP AudioPlaybackCallback으로
받는다. 기존 필수 권한은 유지하며 앱 식별을 위한 QUERY_ALL_PACKAGES를 추가했다.
OPlus RUS 온라인 업데이트·전용 오디오 PID/게임 감지 서비스는 포팅하지 않았다.
따라서 자동 장면 감지가 스톡과 모든 앱/동시 재생 상황에서 같다는 보증은 아니다.
Holo는 별도의 0..5 프로파일 공간에 맞게 변환하며 AudioX 슬롯을 그대로 보내지 않는다.

## 설정 이전 및 검증

기존 음악/영화/게임 선택은 고음질/몰입/스마트로 이전한다. 기존 기본은 스마트로
동작한다. 이전 EQ 값은 보존하되 스톡 범위 ±6 dB로 제한한다. 사용자 지정 모드에서는
EQ가 동작하므로 기존 별도의 EQ 켜기 스위치는 제거했다. 다른 모드는 자체 튜닝을 사용한다.

수행한 검사:

- Android 17 system-server SDK stub 대상 앱 전체 Java 컴파일.
- 현재 ROM framework-res.apk 대상 aapt2 리소스 컴파일/링크.
- 스톡 UI 그림/앱 목록 5개 해시 확인. 원본 엔진/튜닝 72개 고정 해시는 그대로 유지.
- 호스트 테스트: 신형 모드 변환, 영상 회전/이어폰 분기, 구형 선택 이전,
  프리셋 대표 이득과 Q22 값, 스피커/이어폰 저장 분리, 사용자 지정 값 복원,
  잘못된 프리셋 거부 및 기존 EQ 범위 이전.
- 통합 검증: pagani와 모듈/설치 충돌 0개, manifest/권한/QS 진입점 보호 확인.
- git diff --check.

이 UI 변경본의 전체 ROM 빌드·앱 설치·화면 렌더링·실기기 조작은 수행하지 않았다.
앞서 기록된 실기기 AudioX/5.1 Holo 검증은 이 UI 변경 이전 버전에 대한 결과다.
다음 빌드 설치 후 4모드 전환, 스마트의 재생 앱 변경 반영, 프리셋·각 밴드 조절,
사용자 지정 복원, 이어폰 전환, 회전/재부팅, QS 타일 상태·잠금 시 동작을 확인해야 한다.

진단용으로 `adb shell dumpsys activity service com.oplus.oreality.audio/org.lineageos.oreality.EffectService`에서
선택 모드·실제 선택한 stock profile/AudioX slot·장면·출력 그룹·EQ 요청값을 확인할 수 있다.
`desiredEq`는 저장/전송 요청값이며 엔진에서 수치를 다시 읽은 결과라는 뜻은 아니다.
