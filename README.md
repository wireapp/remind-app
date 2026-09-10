# Wire Remind-App (ex. Reminders-Bot)

**Driven by WireSDK using MLS protocol.**

> **Note:** This app is in development and not ready for production use.

## Overview
This is an app that can create reminders for conversation, and send a message when the reminder is due.

## **Features**
- Set one-time reminders
- Set recurrent reminders (e.g., every Monday)
- List active reminders
- Delete reminders (with the Delete button on each reminder)
- Set your personal timezone, so reminder times mean what you expect

> [!IMPORTANT]  
> As of now, the app only supports a maximum of 5 active reminders per group.

## Commands

### The basics of `/remind` command:

- `/remind to <"what"> <"when">`: Sets a reminder for the group conversation, the reminder will be sent to the group when it's due.

### Timezone

Reminder times are read in **your** timezone, so you need to tell the app which one that is.
Because it is a personal setting, it is only accepted in your 1:1 conversation with the app:

```
/remind set-timezone "Europe/Berlin"
```

Use an [IANA timezone name](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones),
for example `Europe/Istanbul`, `America/New_York` or `Asia/Tokyo`. Anything the app cannot
resolve to a real timezone is rejected.

The first time you create a reminder without having set a timezone, the app opens a 1:1
conversation with you, asks for it there, and skips creating that reminder. Send the reminder
command again once your timezone is set.

> [!NOTE]
> A reminder keeps the timezone it was created with. Changing your timezone later affects new
> reminders only, so existing ones never shift for the rest of the conversation.

### `"When"` syntax or setting the time for the reminder:

- By default, all reminders should use 24 hrs format, including minutes.
- If the reminder is recurrent or to happen relative to today, you must add the time in the format `<hh:mm> "21:15"`.

> [!NOTE]  
> You can set reminders for the entire conversation.
>
> You can also set reminders to repeat by the day (ex. every Monday), repetitions by time are not supported (ex. every hour).

### Set reminders examples:

|                    | Command      | What                                           | When                                |
|--------------------|--------------|------------------------------------------------|-------------------------------------|
| one time reminder  | `/remind to` | `"Fill in your invoices by end of day"`        | `"tomorrow at 17:30"`               |
| one time reminder  | `/remind to` | `"Fill in your invoices by end of day"`        | `"next Tue at 17:30"`               |
| one time reminder  | `/remind to` | `"Reply to HR email"`                          | `"in 10 minutes"`                   |
| one time reminder  | `/remind to` | `"Travel back in time to not develop the app"` | `"11/11/2150"`                      |
| recurrent reminder | `/remind to` | `"Join the daily stand-up"`                    | `"every day at 10:00"`              |
| recurrent reminder | `/remind to` | `"Empty the unread emails"`                    | `"every Friday at 17:00"`           |
| recurrent reminder | `/remind to` | `"Empty the unread emails"`                    | `"every Mon, TUE, friday at 17:00"` |

> [!TIP]  
> You can set reminders for yourself. To do so, you can use the commands in a private conversation, a 1:1 with the app.

### Other helpful commands:

- `/remind help` (displays help about command usage)
- `/remind list` (list the active reminders set in the conversation)
- `/remind set-timezone "Europe/Berlin"` (sets your timezone, in the 1:1 with the app)

To delete a reminder, use the **Delete** button shown next to each reminder in the `/remind list` response.

## Development setup

You first need to set all the env variables required by the app itself and the SDK inside.
Check the `.env.sample` and create a new `.env` file while setting the properties.

And also the env variables required by `Wire Applications JVM SDK`, check related README 
[SDK README](https://github.com/wireapp/wire-apps-jvm-sdk/blob/main/README.md)



## Running the application in dev mode

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

If you don't want to mess with Quarkus, use the provided `Dockerfile` or `docker-compose.yml`

Otherwise you can just start the `db` service in docker to have Postgres running and run the app yourself.

You can run your application in dev mode that enables live coding using:
```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./gradlew build
```
It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable inside the `build` directory

If you want to learn more about building native executables, please consult https://quarkus.io/guides/gradle-tooling.

## Related Guides

- Flyway ([guide](https://quarkus.io/guides/flyway)): Handle your database schema migrations
- Quartz ([guide](https://quarkus.io/guides/quartz)): Schedule clustered tasks with Quartz
- Kotlin ([guide](https://quarkus.io/guides/kotlin)): Write your services in Kotlin
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC


