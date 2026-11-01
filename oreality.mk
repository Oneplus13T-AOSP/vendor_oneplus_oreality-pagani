# SPDX-License-Identifier: Apache-2.0
#
# OnePlus 13T (pagani) OReality Audio + Holo Audio.
#
# This repository is installed at vendor/oneplus/oreality-pagani and inherited from
# device/oneplus/pagani/device.mk only. It does not modify sm8750-common.
#
# The effects configuration is installed to /odm/etc/audio_effects_config.xml.
# libaudioeffecthal.qti resolves audio_effects_config.xml through
# audio_find_readable_configuration_file(), whose search order starts with
# /odm/etc, so this file wins over the stock
# /vendor/etc/audio/sku_sun/audio_effects_config.xml without touching it.

TARGET_ENABLE_ONEPlus_OREALITY ?= true

ifeq ($(TARGET_ENABLE_ONEPlus_OREALITY),true)

PRODUCT_SOONG_NAMESPACES += \
    vendor/oneplus/oreality-pagani

# Effect engines and the effects HAL configuration.
PRODUCT_PACKAGES += \
    oreality_audio_effects_config.xml \
    liboplus_audiox \
    libOplusAudioxAidl \
    libOplusSpatializerAidl

# Control app, privileged permission allowlist and OPlus feature declarations.
PRODUCT_PACKAGES += \
    ORealityControl \
    privapp-permissions-oreality

# AudioX is a vendor effect selected by its own type property. The stock
# ColorOS 17 value is "audiox_merge"; without it the AudioX library falls back
# to a single-profile path.
PRODUCT_VENDOR_PROPERTIES += \
    ro.oplus.audio.effect.type=audiox_merge

# AudioService reads this platform property to initialize SpatializerHelper.
PRODUCT_SYSTEM_PROPERTIES += \
    ro.audio.spatializer_enabled=true

# AudioX tuning: 7 headphone profiles and 40 speaker profiles, all from the
# matching ColorOS 17 pagani dump.
PRODUCT_COPY_FILES += \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_COMMON.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_COMMON.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_GAME1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_GAME1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_GAME2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_GAME2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_MOVIE.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_MOVIE.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_MOVIE2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_MOVIE2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_MUSIC.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_MUSIC.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/HP_VOICE.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/HP_VOICE.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_COMMON_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_COMMON_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_COMMON_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_COMMON_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_COMMON_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_COMMON_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_COMMON_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_COMMON_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_COMMON_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_COMMON_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME1_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME1_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME1_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME1_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME1_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME1_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME1_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME1_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME1_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME1_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME2_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME2_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME2_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME2_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME2_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME2_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME2_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME2_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_GAME2_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_GAME2_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE2_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE2_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE2_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE2_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE2_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE2_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE2_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE2_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE2_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE2_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE3_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE3_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE3_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE3_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE3_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE3_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE3_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE3_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE3_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE3_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MOVIE_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MOVIE_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MUSIC_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MUSIC_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MUSIC_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MUSIC_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MUSIC_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MUSIC_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MUSIC_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MUSIC_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_MUSIC_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_MUSIC_lvl5.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_VOICE_lvl1.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_VOICE_lvl1.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_VOICE_lvl2.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_VOICE_lvl2.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_VOICE_lvl3.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_VOICE_lvl3.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_VOICE_lvl4.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_VOICE_lvl4.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/audio/audiox_param/SPK_VOICE_lvl5.bin:$(TARGET_COPY_OUT_ODM)/etc/audio/audiox_param/SPK_VOICE_lvl5.bin

# Shared headphone/speaker spatializer tuning is provided by vendor/pagani.
# Keep only the two additional AudioX room models absent from that tree.
PRODUCT_COPY_FILES += \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/spatializer/SPAT_AUDIOX_ROOMMODEL_MOVIE_SPK.bin:$(TARGET_COPY_OUT_ODM)/etc/spatializer/SPAT_AUDIOX_ROOMMODEL_MOVIE_SPK.bin \
    vendor/oneplus/oreality-pagani/proprietary/odm/etc/spatializer/SPAT_AUDIOX_ROOMMODEL_MUSIC_SPK.bin:$(TARGET_COPY_OUT_ODM)/etc/spatializer/SPAT_AUDIOX_ROOMMODEL_MUSIC_SPK.bin

endif # TARGET_ENABLE_ONEPlus_OREALITY
