# our mobile App Most Polluted

Most Polluted is our Android app for friends and competetive drinkers/smokers to compete with their friends as well as the rest of our users.

## Features

- Firebase email/password authentication with sign up and login flows.
- Required profile picture upload during sign up.
- Editable profile with display name, profile picture, totals, logout, and account deletion.
- Drink and cigarette logging with controlled item choices.
- Required spot name and uploaded log photo for each entry.
- Optional description for logs.
- Location capture for map pins when permission is granted.
- Activity feed showing the current user and friends.
- Feed cards includes profile pictures,images, drink/ciggarette consumed, location,a description preview, and first/second/third rank badges the people in these positions will also have all their feed cards highlighted in gold silver or bronze.
- Expandable feed descriptions that show the first 6 words by default, with More/Less controls.
- Feed map mode showing logs with saved coordinates.
- Friends screen for searching, adding, accepting, and viewing friends.
- Case-insensitive friend search with pending request status.
- Weekly leaderboard that resets every Monday at 00:00.
- Leaderboard separates drink and cigarette rankings.
- Calendar/events screen with event creation, friend invites, and map locations.
- Profile image shown in the toolbar shortcut.
- Editable profile picture from the profile page.
- Password change from the profile page.
- Delete account cleanup for profile, logs, friends, requests, and event links.
- App-wide light/dark mode toggle from the top-left toolbar icon.

## Main Screens

- `LoginActivity` - login entry point.
- `SignUpActivity` - account creation and profile picture setup.
- `MainActivity` - main app shell, toolbar, bottom navigation, and theme toggle.
- `FeedFragment` - activity feed.
- `MapFragment` - map view for consumption logs and events.
- `LogDrinkFragment` - drink/cigarette logging form.
- `FriendsFragment` - friend search, requests, and friend list.
- `LeaderboardFragment` - ranking and totals.
- `CalendarFragment` - events/calendar view.
- `CreateEventFragment` - event creation form with friend invites.
- `LocationPickFragment` - location picker support for events.
- `ProfileFragment` - profile management.

## Permissions

Our app requests:

- Internet access for Firebase and Maps.
- Camera access for profile/log photos.
- location access for map pins and current-location map behavior.

## Notes

- The app requires Firebase services and Google Maps configuration to work fully.
- If location permission is denied map pins may be unavailable.

## ps
- map will always put same location as the location from the emulator is the same
- emulator must be wired to use webcam and not emulator phone camera

fuwhads laptop broke during this project so 2 of my commits were done on henry sampers computer these commits were 
- Improving the ui for the base functionality
- Adding in first second and third badges for feed as well as corresponding colour schemes

