# Android-app installeren en automatisch mailen activeren

## 1. Ontvangstpunt op de website plaatsen

Upload **`server/report.php`** naar de publieke webmap:

```text
dns-diagnose/report.php
```

Het vaste, intern ingestelde ontvangstadres is:

```text
https://www.deheksenketel.com/dns-diagnose/report.php
```

Dit is één los PHP-bestand, geen WordPress-plugin. De hosting moet PHP 8.1+ en WordPress-mail of uitgaande PHP-mail ondersteunen. In een WordPress-installatie laadt het bestand de `wp-load.php` uit de bovenliggende map en gebruikt het `wp_mail()`, inclusief de ingestelde mailintegratie. Het vaste ontvangstadres is **info@deheksenketel.com**. Laat zo nodig de hostingbeheerder de afzender en mailaflevering instellen. Er zijn geen mailwachtwoorden in de app nodig.

Open het HTTPS-adres in de browser. Je hoort HTTP 405 met `POST required` te krijgen: het bestand werkt en wacht op een rapport. Een 404 betekent dat het nog niet op de juiste plek staat. De app volgt voor rapportuploads geen redirects. Het ontvangstadres wordt sinds appversie 1.1 niet weergegeven en is niet via de app aanpasbaar. Een ander adres vereist een nieuwe appbuild. Het adres blijft technisch afleidbaar uit de APK of netwerkverbindingen; verbergen is geen toegangsbeveiliging.

## 2. APK installeren

Kopieer **`Heksenketel-Netwerktest.apk`** uit de map `downloads` naar je Android-telefoon en open het bestand. Sta wanneer gevraagd installatie via de gebruikte bestandsapp toe. Android 8.0 of nieuwer is vereist. Dit is een debug-ondertekende installatiebuild voor eigen gebruik.

## 3. Testen

1. Open **Heksenketel Netwerktest**.
2. Het ontvangstadres is intern ingesteld; er hoeft niets te worden ingevuld.
3. Druk op **Test verbinding en verstuur rapport**.
4. Wacht op de uitslag. Het rapport wordt automatisch aangeboden voor e-mail.
5. Controleer de mailbox en spammap van **info@deheksenketel.com**.

De app meldt alleen dat de server het rapport voor verzending heeft aangenomen; dat is geen garantie op aflevering. Als het versturen mislukt, blijft het rapport op de telefoon en kun je opnieuw versturen. Een volgende test probeert ook eerdere opgeslagen rapporten te versturen.

Herhaal via wifi en mobiele data van een andere provider. De app legt de beschikbare netwerkgegevens vast. Een wifi-internetprovider kan niet altijd automatisch worden herkend; een mobiele operatornaam is waar beschikbaar een aanwijzing.

De app voert geen continue monitoring uit. Elke meting begint met de testknop; daarna verloopt het versturen automatisch. Lokale rapporten verdwijnen bij verwijdering van de app.

## Wat is al gecontroleerd?

- Android APK-build geslaagd.
- 7 Android/JVM-tests voor DNS-pakketverwerking geslaagd.
- Android lint: geen fouten (wel waarschuwingen over doel-Androidversie en Nederlandse tekststrings).
- 4 PHP-integratietests geslaagd: daadwerkelijke lokale HTTP-verzoeken met nagebootste mailtransportdiensten, waaronder duplicaatdetectie, limieten, ontbrekende PHP-mail en WordPress-mail.
- De APK is op 18 oktober 2026 geïnstalleerd en gestart in een Android 16-emulator (Google TV-systeemimage). Testknop, DNS, HTTPS en bewaren bij uploadfout zijn uitgevoerd; geen Android-runtimecrash vastgesteld. Dit vervangt geen praktijktest op een fysieke telefoon.
- Het live ontvangstpunt reageerde correct op GET (405) en een ongeldig rapport (400), maar gaf bij het echte emulatorrapport HTTP 500. De precieze serveroorzaak is zonder PHP-log niet vastgesteld. De aangepaste versie van `server/report.php` ondersteunt nu WordPress-mail en vangt serverfouten af. Die versie moet nog op de hosting worden geplaatst. De live website is vanuit deze werkomgeving niet gewijzigd en mailaflevering is niet bevestigd.

Uitgebreide technische informatie: `android/README.md`.

### Update

Na het vervangen van het ontvangstbestand antwoordde de live server met HTTP 200 en `accepted: true`. De gebruiker heeft vervolgens bevestigd dat de e-mailverzending werkt. Appversie 1.1 verbergt de hostinginstellingen en gebruikt het vaste ontvangstadres; build en lintcontrole zijn geslaagd.

Versie 1.2 toont bovenaan **Netwerktest · v1.2**, zodat de geïnstalleerde versie direct herkenbaar is. Installeer `Heksenketel-Netwerktest-v1.2.apk` uit Downloads als update over de bestaande app. De app bevat geen adresinvoerveld. Zie je nog een invoerveld of ontbreekt de aanduiding v1.2, dan wordt niet deze versie weergegeven. Controleer bij Android **App-info** dat de versie 1.2 is en de pakketnaam `com.deheksenketel.netwerktest`.
