# Assistant Button

Android utility for opening a voice assistant with the headset button and closing its voice panel with the same button. You choose the assistant on your phone. Repeat commands send Android Back only when the voice panel you confirmed is in focus.

## Setup

1. Install `app-debug.apk` from [Releases](https://github.com/avebeatrix/assistant-button/releases) or run `adb install -r app/build/outputs/apk/debug/app-debug.apk` after building.
2. Open **Assistant Button → Choose assistant** and select your installed assistant. **Test assistant launch** checks that it opens in voice mode. If it opens a different screen, try **Try ASSIST first**.
3. In Android Accessibility settings, enable **Close the voice panel**. In Assistant Button, choose **Set up panel closing → Open voice panel**. Wait a second, press Android Back to return, then choose **Confirm detected window → Save voice panel** if the voice panel opened and Back closed it.
4. Set the headset button to call the voice assistant. Invoke it and choose **Assistant Button → Always** in the Android app chooser. If another app opens immediately, clear its defaults in Android settings or select **Assistant Button** in **Android assistant settings** if your phone uses that setting to route the button.
5. Hold the headset button once to open the assistant. Release it, then hold again while the panel is visible. Repeat starting with the phone locked and its screen off if you use that mode.

To restore the previous handler, clear Assistant Button's defaults or uninstall it, then choose your previous assistant the next time you hold the headset button. Revert Android's default assistant selection if you changed it.

## Compatibility and diagnostics

Android 10 or later. No root or computer is needed after setup. The headset and phone have to deliver both presses as Android voice commands. A locked phone may restrict app launches or hide the active window from accessibility. Assistants that do not expose a compatible launch intent, or that do not report a distinct voice panel, may have limited support. If a panel changes after updating the assistant, set it up again.

The local command log distinguishes a received command (`incoming`), a requested launch (`launch=requested`), and a Back request (`decision=back`). Turn on **Log commands only** to test command delivery without launching or closing the assistant. The app uses a window's package and class to identify it. It does not save screen text, record audio, or send data over the network.

```sh
adb logcat -v threadtime -T 1 -s AssistantButton ActivityTaskManager HeadsetService HeadsetStateMachine
```

On an Android 16 phone, the assistant selection and panel setup were tested through the app UI. Two ADB voice commands verified opening and closing the configured panel. Headset compatibility depends on the phone and headset delivering the commands.

## Build

Use JDK 17 or 21 and Android SDK 36.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

## По-русски

Выберите ассистента в приложении **Кнопка ассистента** и включите службу **Закрытие голосовой панели**. Нажмите **Настроить закрытие панели**, дождитесь голосового окна, вернитесь системной кнопкой «Назад» и подтвердите найденное окно. При удержании кнопки гарнитуры выберите **Кнопка ассистента → Всегда**. Первое удержание открывает голосовой ввод, повторное закрывает подтверждённую панель.

## License

Project code is available under the [MIT License](LICENSE).
