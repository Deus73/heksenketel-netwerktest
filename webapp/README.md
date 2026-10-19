# Heksenketel Netwerktest — webapp v1.3

Installeerbare webapp voor Android, iPhone/iPad en desktop. De gebruiker start een meting; daarna wordt het rapport automatisch aangeboden voor e-mail naar `info@deheksenketel.com`. Het ontvangstadres is intern vastgelegd en heeft geen instelveld.

## Installeren op de webhosting

1. Download `Heksenketel-Webapp-v1.3.zip` bij de GitHub-release en pak het lokaal uit.
2. Upload de map `dns-diagnose` naar de publieke webroot van **www.deheksenketel.com**. Deze bevat `app/`, `diagnose.php` en `dns.php`.
3. **Behoud het bestaande werkende `dns-diagnose/report.php`.** Dit bestand is bewust niet in de webapp-ZIP opgenomen. Bij een geheel nieuwe installatie kun je `server/report.php` uit de repository op die plek zetten en de mailconfiguratie inrichten.
4. PHP 8.1+ is nodig voor de servermetingen. Uitgaande UDP/TCP op poort 53 kan door hosting worden beperkt; dat verschijnt als meetfout en bewijst geen providerblokkade.
5. Open **https://www.deheksenketel.com/dns-diagnose/app/index.html** en start een test.
6. Controleer het rapport en de mailbox. De app bevestigt acceptatie voor verzending, geen aflevering.

De bestanden zijn niet automatisch op je hosting geplaatst. Een profiel dat de webapp opent werkt pas nadat bovenstaande bestanden zijn geüpload. Er zijn geen JavaScript-bundler, Node-server of databases nodig.

## Installeren op het beginscherm

- **iPhone/iPad:** gebruik het gekoppelde `.mobileconfig`-profiel of Safari → Deel → Zet op beginscherm.
- **Android:** open de webapp in Chrome en kies Installeren/Toevoegen aan startscherm indien aangeboden.
- **Desktop:** gebruik de installatieoptie van je browser indien beschikbaar.

Gebruik een recente browser met HTTPS, JavaScript en lokale websiteopslag. De server kan bij PHP-fouten of drukte een foutstatus teruggeven; het rapport wordt dan met de beschikbare browsermetingen bewaard en aangeboden voor e-mail.

## Welke metingen vinden waar plaats?

| Meting | Uitvoering |
|---|---|
| Website-bereikbaarheid | Browser van de gebruiker, certificaatcontrole door browser |
| Cloudflare/Google DNS over HTTPS | Rechtstreeks vanuit de browser; niet de systeem-DNS |
| Proximus/Telenet/Cloudflare/Google/Quad9 via UDP, TCP bij truncatie | Webhostingserver; **niet vanaf de aansluiting van de gebruiker** |
| Netwerkhint zoals effectiveType | Browser, indien beschikbaar; geen betrouwbare providernaam |

De browser kan de functies van de native app niet één op één overnemen. Er is geen directe UDP/TCP-toegang tot providerresolvers, geen systeem-DNS-uitlezing, geen betrouwbare operator/VPN-detectie en geen HTTPS-test per gevonden IP met aangepaste SNI. Browser-, TLS- en CORS-fouten zijn niet altijd te onderscheiden. Een ontvangen DNS-antwoord wordt vastgelegd; de server controleert geen DNSSEC en vraagt een losse CNAME-vervolgketen niet opnieuw op.

Cloudflare en Google moeten browsertoegang (CORS) tot hun JSON-DoH-dienst toestaan; blokkade of wijziging van die dienst verschijnt als fout. Een gebruikersverbinding met een blokkade kan ook het versturen naar het eigen ontvangstpunt verhinderen.

## Offline en rapporten

De serviceworker bewaart uitsluitend de appbestanden. Metingen en rapportuploads worden nooit uit de cache beantwoord. De pagina kan dus offline openen, maar een netwerkmeting kan offline niet slagen. Wachtende rapporten en het laatste rapport staan in localStorage van deze browser. Maximaal twintig wachtende rapporten; er wordt niets stilzwijgend weggegooid. Bij een nieuwe test of druk op de herhaalknop worden ze opnieuw aangeboden. Geen continue achtergrondmeting of gegarandeerde verzending na sluiten van de app.

Op iOS kunnen Safari en de beginschermwebapp verschillende lokale opslag hebben. Gebruik voor één meetreeks steeds dezelfde geopende webapp.

## Ontwikkelen

De [testresultaten](TESTRESULTATEN.md) beschrijven de uitgevoerde browser- en backendcontroles.

Bronnen: `webapp/`, `server/diagnose.php`, `server/dns.php` en het bestaande `server/report.php`. `tools/make_web_icons.py` genereert de PNG-iconen zonder externe Python-pakketten. De server accepteert alleen een vast domein en vaste DNS-servers en beperkt meetaanvragen per bron-IP. Achter een proxy moet de webserver de echte client-IP uitsluitend via vertrouwde proxies vaststellen.
