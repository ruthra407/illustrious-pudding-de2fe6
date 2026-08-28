/*
 * PATCH FOR ReminderVoiceService.java
 *
 * Replace ONLY the TTS engine fields + onCreate TTS initialization +
 * onInit() in the current file with the sections below.
 *
 * No notification/reminder/data logic is changed.
 */

/* ---------- 1. Add these fields ---------- */

private java.util.List<TextToSpeech.EngineInfo> installedEngines;
private int engineTryIndex = -1;


/* ---------- 2. In onCreate(), replace the existing TTS creation ---------- */

textToSpeech =
        new TextToSpeech(
                getApplicationContext(),
                this
        );


/* ---------- 3. Replace onInit() with this ---------- */

@Override
public void onInit(int status) {

    Log.d(
            TAG,
            "========== TTS ENGINE DIAGNOSTIC =========="
    );

    Log.d(
            TAG,
            "TTS INIT STATUS = " + status
    );

    if (textToSpeech == null) {

        Log.e(
                TAG,
                "TTS OBJECT = NULL"
        );

        return;
    }

    try {

        selectedEnginePackage =
                textToSpeech.getDefaultEngine();

        Log.d(
                TAG,
                "ACTIVE TTS ENGINE = " +
                        selectedEnginePackage
        );

    } catch (Exception e) {

        Log.e(
                TAG,
                "DEFAULT ENGINE CHECK FAILED",
                e
        );
    }

    if (status != TextToSpeech.SUCCESS) {

        Log.e(
                TAG,
                "DEFAULT TTS INITIALIZATION FAILED = " +
                        status
        );

        if (tryNextInstalledEngine()) {
            return;
        }

        Log.e(
                TAG,
                "NO WORKING INSTALLED TTS ENGINE"
        );

        ttsReady = false;

        Log.d(
                TAG,
                "========== END TTS DIAGNOSTIC =========="
        );

        return;
    }

    inspectTamilVoices();

    boolean tamilVoiceSelected =
            selectInstalledTamilVoice();

    if (tamilVoiceSelected) {

        ttsReady = true;

        Log.d(
                TAG,
                "ANDROID TAMIL VOICE READY"
        );

        attachUtteranceListener();

        return;
    }

    boolean languageReady =
            tryTamilLanguageFallback();

    if (languageReady) {

        ttsReady = true;

        Log.d(
                TAG,
                "ANDROID TAMIL LANGUAGE READY"
        );

    } else {

        ttsReady = false;

        Log.e(
                TAG,
                "THIS ENGINE HAS NO USABLE TAMIL VOICE"
        );
    }

    attachUtteranceListener();

    Log.d(
            TAG,
            "========== END TTS DIAGNOSTIC =========="
    );
}


/* ---------- 4. Add these helper methods ---------- */

private boolean tryNextInstalledEngine() {

    try {

        if (textToSpeech == null) {
            return false;
        }

        if (installedEngines == null) {

            installedEngines =
                    textToSpeech.getEngines();

            engineTryIndex = -1;

            Log.d(
                    TAG,
                    "INSTALLED TTS ENGINE COUNT = " +
                            (installedEngines == null
                                    ? 0
                                    : installedEngines.size())
            );
        }

        if (installedEngines == null ||
                installedEngines.isEmpty()) {

            return false;
        }

        while (engineTryIndex + 1 <
                installedEngines.size()) {

            engineTryIndex++;

            TextToSpeech.EngineInfo info =
                    installedEngines.get(
                            engineTryIndex
                    );

            if (info == null ||
                    info.name == null) {

                continue;
            }

            Log.d(
                    TAG,
                    "TRYING TTS ENGINE = " +
                            info.name +
                            " / " +
                            info.label
            );

            try {

                if (textToSpeech != null) {
                    textToSpeech.shutdown();
                }

            } catch (Exception ignored) {
            }

            textToSpeech =
                    new TextToSpeech(
                            getApplicationContext(),
                            this,
                            info.name
                    );

            selectedEnginePackage =
                    info.name;

            return true;
        }

    } catch (Exception e) {

        Log.e(
                TAG,
                "INSTALLED ENGINE FALLBACK FAILED",
                e
        );
    }

    return false;
}private void inspectTamilVoices() {

    if (textToSpeech == null ||
            Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.LOLLIPOP) {

        return;
    }

    try {

        int languageStatus =
                textToSpeech.isLanguageAvailable(
                        new Locale(
                                "ta",
                                "IN"
                        )
                );

        Log.d(
                TAG,
                "TA-IN AVAILABILITY = " +
                        languageStatus
        );

        Set<Voice> voices =
                textToSpeech.getVoices();

        int tamilCount = 0;

        if (voices != null) {

            for (Voice voice : voices) {

                if (voice == null ||
                        voice.getLocale() == null) {

                    continue;
                }

                Locale locale =
                        voice.getLocale();

                if (!"ta".equalsIgnoreCase(
                        locale.getLanguage()
                )) {

                    continue;
                }

                tamilCount++;

                Log.d(
                        TAG,
                        "TAMIL VOICE NAME = " +
                                voice.getName()
                );

                Log.d(
                        TAG,
                        "TAMIL VOICE LOCALE = " +
                                locale
                );

                Log.d(
                        TAG,
                        "TAMIL VOICE FEATURES = " +
                                voice.getFeatures()
                );
            }
        }

        Log.d(
                TAG,
                "TOTAL TAMIL VOICES = " +
                        tamilCount
        );

    } catch (Exception e) {

        Log.e(
                TAG,
                "TAMIL VOICE INSPECTION FAILED",
                e
        );
    }
}


/*
 * IMPORTANT:
 * Do not add another ReminderVoiceService class,
 * package line, or second onInit().
 *
 * These sections replace the corresponding sections
 * in the current file.
 */
