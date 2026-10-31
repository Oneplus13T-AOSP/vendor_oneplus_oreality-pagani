# SPDX-License-Identifier: Apache-2.0
#
# OnePlus 13T (pagani) OReality Audio + Holo Audio board configuration.
#
# Include from device/oneplus/pagani/BoardConfig.mk. No SELinux policy is added:
# both effects are loaded by the existing hal_audio_default effects HAL and the
# control app runs as a privileged platform app.

ifeq ($(TARGET_ENABLE_ONEPlus_OREALITY),true)
# The effects are ODMs; the control app lives on system.
endif
