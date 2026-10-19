# Heksenketel Netwerktest — webapp, Android en Apple-profiel

**Test de bereikbaarheid van www.deheksenketel.com en stuur je bevindingen automatisch naar De Heksenketel.**

## Webapp voor iPhone, iPad, Android en desktop

**[Open de webapp](https://www.deheksenketel.com/dns-diagnose/app/index.html)** · **[Download webhostingpakket v1.3](https://github.com/Deus73/heksenketel-netwerktest/releases/download/v1.3/Heksenketel-Webapp-v1.3.zip)**

De webapp moet eenmalig op de hosting worden geplaatst; zie de [installatiehandleiding](webapp/README.md). Het uploadpakket behoudt het bestaande werkende mailontvangstpunt. De webapp doet browsermetingen en apart gelabelde servermetingen en verstuurt het rapport automatisch. Browserbeperkingen betekenen dat de metingen niet identiek zijn aan alle native Android-functies.

**[⬇ Download Apple-profiel (.mobileconfig)](https://github.com/Deus73/heksenketel-netwerktest/releases/download/v1.3/De-Heksenketel.mobileconfig)**

Het Apple-profiel opent deze webapp vanaf het beginscherm. Zie de [Apple-installatiehandleiding](apple/README.md).

## Download en installeer de app

**[⬇ Download Android-app v1.2 (APK)](https://github.com/Deus73/heksenketel-netwerktest/releases/download/v1.2/Heksenketel-Netwerktest-v1.2.apk)**

**[Bekijk de releases en alle downloadbestanden](https://github.com/Deus73/heksenketel-netwerktest/releases/latest)**

1. Open de APK-downloadlink op je Android-telefoon.
2. Open het gedownloade bestand en geef Android zo nodig toestemming om via je browser of bestandsapp te installeren.
3. Installeer de app, of kies **Bijwerken** als je een eerdere versie hebt.
4. Controleer bovenaan de app de titel **Netwerktest · v1.2**.
5. Druk op **Test verbinding en verstuur rapport**.

**Android 8.0 of nieuwer vereist.** Gebruik de APK bij de release, niet GitHubs automatisch gegenereerde ‘Source code’-ZIP; die ZIP bevat alleen broncode.

## Wat doet de app?

### Android-netwerktest

- Test uitsluitend `www.deheksenketel.com`.
- Vergelijkt A/AAAA- en CNAME-antwoorden van de DNS-servers van de huidige aansluiting, een Proximus/Telenet-startlijst en Cloudflare, Google en Quad9.
- Controleert de HTTPS-bereikbaarheid met certificaatvalidatie en legt eventuele redirects vast.
- Legt netwerktype en, waar beschikbaar, een mobiele operatornaam vast.
- Biedt het rapport automatisch aan voor e-mail naar **info@deheksenketel.com**.
- Bewaart rapporten lokaal wanneer versturen mislukt, met een knop om opnieuw te proberen.

Het ontvangstadres is intern vast ingesteld: het staat niet op het scherm en is niet aanpasbaar via de app. De app voert alleen metingen uit na een druk op de testknop.

## Wat betekenen de resultaten?

Een time-out bij een externe providerresolver is **geen bewijs van blokkade**: sommige resolvers antwoorden uitsluitend voor eigen klanten. Verschillende IP-adressen kunnen normaal zijn bij een CDN. Alle verzoeken lopen via je huidige aansluiting; het raadplegen van een andere DNS-server geeft je geen internetaansluiting bij die provider.

Vergelijk een test op wifi met een test op mobiele data van een andere provider. Een mobiele operatornaam is een aanwijzing; op wifi kan de internetprovider niet altijd automatisch worden herkend. De app onderzoekt geen Google-zoekresultaten of AI-antwoorden.

## Welke gegevens worden verstuurd?

Tijdstip, een willekeurige rapport-ID, netwerksoort, beschikbare operatornaam, DNS-antwoorden en HTTPS-resultaten. Geen locatie, contacten, telefoonnummer, permanente toestel-ID of browsegeschiedenis. Het ontvangstpunt ziet technisch het internet-IP. Lokale rapporten worden verwijderd wanneer je de app verwijdert.

De melding ‘aangenomen voor verzending’ bevestigt acceptatie door de mailfunctie, niet aflevering in de mailbox.

## Voor beheerders en ontwikkelaars

- [Android-broncode en bouwinstructies](android/README.md)
- [Ontvangstpunt en installatie op webhosting](INSTALLATIE-ANDROID.md)
- [Python-versie voor aanvullende DNS- en IP-tests](PYTHON-README.md)
- [Wijzigingen in v1.2](CHANGELOG.md)

De APK is een debug-ondertekende installatiebuild. De privé-ondertekeningssleutel staat niet in deze repository. Zelfgebouwde APK's hebben doorgaans een andere handtekening en kunnen daardoor niet zonder meer als update over deze download worden geïnstalleerd.

## Controles

De DNS-pakketverwerking is getest met JVM-tests; het PHP-ontvangstpunt is getest met lokale HTTP-verzoeken en nagebootste mailtransporten. De v1.2-interface is gecontroleerd in een Android 16-emulator: geen zichtbaar ontvangstadres of invoerveld. Build en lintcontrole slagen.
