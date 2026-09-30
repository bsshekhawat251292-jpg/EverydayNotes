# Everyday Notes — Play-ready v2

A genuine offline notes utility with create/edit/delete, search, categories, pinning, sharing and dark mode.

Target API: 36
Package: com.bhavyadigital.everydaynotes
Version: 1.0 (1)

No network, location, contacts, SMS, storage or other sensitive permissions are declared.
No ads or analytics SDKs are included.

Before production: build a signed release AAB with your own upload key, test on a physical device, and complete all current Play Console declarations and testing requirements.


## v2 note-saving fix
The note editor now uses an explicit Save click handler and confirms the SharedPreferences write before dismissing the dialog. A successful save shows a `Note saved` confirmation.
