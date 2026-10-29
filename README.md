# CallLater

Turn **CallLater** on during a meeting. Callers get a text ("I am in a meeting. Call you later.")
and the call is ended. With the call-screening option enabled, the phone never rings.

## How it behaves
| Situation | Result |
|---|---|
| Call-screening app set (Android 10+) | No ringtone. SMS is sent, then the call is rejected |
| Not set / older Android | Phone may ring for a moment, SMS is sent, then the call is ended |
| Number is private, or caller isn't a contact (contacts-only on) | No text; the call rings silently (if "Silence the ringtone" is on) |
| You answer before the reply | No text is sent |
| Same number calls again within 30 min | Call is ended again, no second text |

A call is only ever ended after the text has been submitted.

## Build the APK on GitHub
1. Upload the contents of this ZIP to your repo (the `app` folder and the top-level files).
2. Make sure `.github/workflows/android-apk.yml` exists (create it on the GitHub website if the upload skipped it).
3. Actions → **Build CallLater APK** → download the **CallLater-apk** artifact → unzip → `CallLater.apk`.

**Uninstall the old version before installing** — every GitHub build is signed with a new debug key, so Android won't update over it.

## First-time setup on the phone
1. Install (turn off Play Protect scanning briefly if it blocks the install).
2. Open CallLater → allow SMS, Phone & call log, Contacts.
3. If Allow is greyed out: tap **Allow restricted settings** in Setup → ⋮ → Allow restricted settings.
4. Tap **Call screening → Set up** and choose CallLater (this is what stops the ringtone).
5. Set **Battery** to Unrestricted. On Xiaomi/Oppo/Vivo/OnePlus also enable Autostart.
6. Use **Test → Send**, then call the phone from another number.

## Limits
Cellular calls only; withheld numbers can't be texted; SMS charges may apply; the call-screening role replaces
Google/Samsung spam protection while selected; not distributable on Google Play.
