# Calendar Notifications Plus React Native

## Building

```bash
yarn 
yarn start

```

## Running an emulator

```bash
cd C:\Users\<username>\appdata\local\android\sdk
.\emulator\emulator.exe -avd 7.6_Fold-in_with_outer_display_API_34q
```

## Wsl setup

```bash

# get the wsl2 ip address
ifconfig eth0 | grep 'inet '

# open the developer menu in the react native activity once loaded
./adb.exe shell input keyevent 82

# input the aformentioned ip address in the developer menu
./adb.exe shell input text "172.21.60.143:8081"

```
