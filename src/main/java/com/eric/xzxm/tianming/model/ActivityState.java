package com.eric.xzxm.tianming.model;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActivityState {
    public boolean active;
    public boolean testMode;
    public boolean ascended;
    public UUID holderId;
    public String holderName;
    public String disguiseForm = "VILLAGER";
    public long startedAt;
    public long nextExposureAt;
    public long dayLengthMillis;
    public long exposureIntervalMillis;
    public int currentDay;

    public final Set<String> unlocked = new LinkedHashSet<>();
    public final Set<Integer> rewardDaysGranted = new LinkedHashSet<>();
    public final Map<Integer, String> loadout = new HashMap<>();
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Long> activeUntil = new HashMap<>();
    public final Map<String, Long> castCooldowns = new HashMap<>();
    public final Map<String, Long> testDurationOverrides = new HashMap<>();
    public final Map<String, Long> testCooldownOverrides = new HashMap<>();

    public void reset() {
        active = false;
        testMode = false;
        ascended = false;
        holderId = null;
        holderName = null;
        disguiseForm = "VILLAGER";
        startedAt = 0L;
        nextExposureAt = 0L;
        currentDay = 0;
        dayLengthMillis = 0L;
        exposureIntervalMillis = 0L;
        unlocked.clear();
        rewardDaysGranted.clear();
        loadout.clear();
        cooldowns.clear();
        activeUntil.clear();
        castCooldowns.clear();
        testDurationOverrides.clear();
        testCooldownOverrides.clear();
    }
}
