# Heksenketel Netwerktest voor Android

Native Android-app, Android 8.0+ (API 26). Eén knop start de test en biedt het rapport daarna automatisch aan voor e-mail naar **info@deheksenketel.com**. Geen mailapp nodig.

## Eenmalig ontvangstpunt installeren

1. Plaats `../server/report.php` via je hostingbeheer in de publieke map `dns-diagnose/report.php` van `www.deheksenketel.com`.
2. PHP 8.1+ en werkende WordPress-mail of PHP `mail()` zijn nodig. Wanneer `wp-load.php` in de bovenliggende map staat, gebruikt de ontvanger `wp_mail()` en de ingestelde WordPress-mailintegratie. Laat je host uitgaande mail voor `info@deheksenketel.com` configureren, inclusief de bijbehorende SPF/DKIM-instellingen indien nodig.
3. Controleer het exacte HTTPS-adres. Een GET daarop hoort HTTP 405 met JSON te geven. Een redirect werkt niet. Het adres is sinds versie 1.1 intern vast ingesteld in `TestService.REPORT_ENDPOINT`; er is geen zichtbaar of aanpasbaar veld in de app. Een adreswijziging vereist een nieuwe build. Dit verbergt het adres in de interface, maar maakt het netwerkadres niet geheim.
4. Voer een test uit en controleer of de mail aankomt (ook spam). De app meldt dat het ontvangstpunt het rapport heeft **aangenomen voor verzending**. Dit garandeert geen aflevering.

De website is vanuit deze werkomgeving niet aangepast. Het ontvangstpunt moet nog op je eigen hosting worden geplaatst. Een WordPress-host kan dit meestal als los PHP-bestand uitvoeren; controleer dit bij de host. Een API- of SMTP-wachtwoord hoort niet in de APK.

Het ontvangstpunt heeft een vaste ontvanger/afzender, 128 KiB limiet, schemavalidatie, maximaal 5 nieuwe rapportpogingen per bron-IP per uur en UUID-deduplicatie gedurende 7 dagen. Het is een publiek meldpunt: rapporten zijn clientmetingen, geen geauthenticeerde bewijsstukken. Achter een reverse proxy/CDN kan REMOTE_ADDR een gedeeld proxyadres zijn: configureer dan de echte client-IP-herkenning bij de webserver voor uitsluitend vertrouwde proxies. De PHP-code vertrouwt geen willekeurige forwarded headers.

## App installeren en gebruiken

Installeer de gegenereerde APK op je Android-toestel en sta installatie vanuit de gekozen bestandsapp toe wanneer Android daarom vraagt. De APK is een debugbuild voor eigen gebruik; voor openbare distributie is een eigen release-ondertekening nodig.

- Open de app en druk op **Test verbinding en verstuur rapport**. Er zijn geen hostinginstellingen zichtbaar of nodig voor gebruikers.
- De app houdt gedurende de test een zichtbare foreground-service actief; je kunt het scherm verlaten.
- Het rapport wordt eerst lokaal opgeslagen, daarna maximaal drie keer automatisch aangeboden.
- Bij mislukking blijft het in de wachtrij. Gebruik **Opgeslagen rapporten opnieuw versturen**. Een volgende test verstuurt ook eerdere wachtende rapporten.
- Een geforceerde stop/procesbeëindiging kan een lopende test afbreken. Voltooide en opgeslagen rapporten blijven bewaard. Er is geen permanente achtergrondmonitor en er wordt geen test bij het opstarten van de telefoon uitgevoerd.
- **Laatste rapport delen als tekst** is beschikbaar als alternatief om het bestand te bekijken of te delen.

## Meetbereik

- De DNS-servers die Android voor de huidige verbinding meldt, plus Proximus/Telenet-startlijst en Cloudflare/Google/Quad9.
- A/AAAA, CNAME, TTL, foutcodes, tijdsduur en TCP-herhaling bij afgekapt UDP-antwoord.
- Systeemresolutie en een HTTPS-test via het netwerk dat bij de start actief was. Alle DNS- en HTTPS-verzoeken zijn aan dat netwerk gebonden. Verander je tijdens de test van netwerk, dan kan de meting of upload mislukken; start opnieuw op de nieuwe verbinding.
- HTTPS gebruikt de systeemresolver, valideert certificaten en volgt redirects niet. Anders dan het Python-script test deze eerste Android-versie **niet ieder extern gevonden IP-adres afzonderlijk**.
- Netwerksoort, VPN-indicatie, private-DNS-status en waar beschikbaar een mobiele operatornaam. Voor wifi kan een app zonder aanvullende dienstverlening niet betrouwbaar de internetprovider afleiden. Er worden geen gevoelige telefoonrechten gevraagd; een operatornaam is geen sluitend providerbewijs.

Geen locatie, SSID, contacten, telefoonnummer, permanente toestel-ID of browsegeschiedenis. Het ontvangstpunt ziet technisch het bron-IP; dat wordt door deze PHP-code alleen gehasht voor tijdelijke rate limiting en niet in de e-mail gezet. Hostinglogs kunnen wel IP-adressen bevatten. Appgegevens zijn uitgesloten van Android-backup; verwijderen van de app verwijdert de lokale rapporten. Er wordt niet automatisch opgeruimd tijdens het gebruik.

Time-outs van externe provider-DNS zijn geen bewijs van websiteblokkades. Vergelijk tests op wifi en mobiele data van een andere provider. De app onderzoekt geen Google-resultaten of AI-antwoorden.

## Bouwen

Open deze map in Android Studio (JDK 17, SDK 35), of gebruik de Gradle-wrapper:

```sh
./gradlew assembleDebug
```

Windows: `gradlew.bat assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Bronnen voor de meegeleverde provider-DNS staan in `../resolvers.json`; de lijst is niet volledig en kan veranderen.
