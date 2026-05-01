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
- Leaderboard for comparing user totals.
- Calendar/events screen.
- App-wide light/dark mode toggle from the top-left toolbar icon.

## Main Screens

- `LoginActivity` - login entry point.
- `SignUpActivity` - account creation and profile picture setup.
- `MainActivity` - main app shell, toolbar, bottom navigation, and theme toggle.
- `FeedFragment` - activity feed and map view.
- `LogDrinkFragment` - drink/cigarette logging form.
- `FriendsFragment` - friend search, requests, and friend list.
- `LeaderboardFragment` - ranking and totals.
- `CalendarFragment` - events/calendar view.
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
fuwhads laptop broke during this project so 2 of my commits were done on henry sampers computer these commits were 
-improving the ui for the base functionality
-adding in first second and third badges for feed as well as corresponding colour schemes

