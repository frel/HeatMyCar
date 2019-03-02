# HeatMyCar

Control the Mitsubishi PHEV AC remotely using SMS.

This simple app controls the AC (heat) by using the Outlander PHEV1 (Remote Control) app.

Only the PHEV1 version is supported (package name: `com.inventec.iMobile1`), but seeing as the apps look identical, simply changing the package name should suffice to support the other versions.
E.g. the first PHEV app's package name is `com.inventec.iMobile`

## Requirements
Dedicated Android device with a sim card that can send/receive SMS. 
Android KitKat 4.4 or above will work. 

Root is _not_ required. 
But the app needs to be granted admin permission (to lock the screen) and be setup as an Accessibility Service in order to interact with the Outlander PHEV app.
The app will ask for the user to grant these permissions, but the Accessibility Service must be setup manually. Google it.

## Required initial setup

### VIN Registration
The Outlander PHEV app needs to be successfully setup with the car. I.e. the VIN registration must be completed as per the manual.
Heating should be selected with the desired duration e.g. 30 minutes for cold winters. If the app works as intended, the required setup is successful.

### Favorite contacts
Only contacts added to the contact list and Favorites ("starred") will be able to send commands. Incoming SMS from anyone else will be ignored. 
 
## Usage
Simply send an SMS with any supported commands to the device's phone number.

In practice you start the heating program by sending the SMS

```heat```

Supported commands:

- `heat`
- `status`
- `battery`
- `abort`
- `reset`
- `batterywarnings`
- `help`

Most commands will also report the device battery charge for convenience.

## Battery optimisations
WiFi and mobile data takes a lot of battery so the app uses SMS for communication.
Since the device needs to connect with the car's WiFi to operate the Outlander PHEH app,
HeatMyCar will connect/disconnect the WiFi to save battery.

## Battery warnings
When a command is received (from a favorite contact), that number is stored.
Every few hours a battery check is made. If the charge is below 10% _one_ SMS is sent to the stored number.

When the charge increases above 80% the battery warning is reset and will again send warnings when the charge drops below 10%.

This featured can be enabled/disabled by sending `batterywarnings`   

## Language

Currently commands can be sent in English or Norwegian.
All replies are in Norwegian, but can easily be translated.

## Limitations

Currently the code for checking if the Outlander App is already heating does not work. This is a limitation
to not requiring rooted devices, but doesn't matter in practice.

If someone opens the door while the car is heating, it will cancel the heat cycle. But HeatMyCar will still wait 30 minutes before allowing another cycle.