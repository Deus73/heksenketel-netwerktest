# Heksenketel Netwerktest v1.3 — webapp en Apple-profiel

## Downloads

- **Heksenketel-Webapp-v1.3.zip** — uploadpakket voor de webhosting.
- **De-Heksenketel.mobileconfig** — iPhone/iPad-beginschermicoon dat de webapp opent.
- **Heksenketel-Netwerktest-v1.2.apk** — de bestaande native Android-app, eveneens beschikbaar als download.
- **SHA256SUMS.txt** — controle van de downloadbestanden.

## Eenmalig installeren op de hosting

Pak het webapp-ZIP-bestand uit en upload de map `dns-diagnose` naar de publieke webroot van `www.deheksenketel.com`. Behoud het bestaande werkende `dns-diagnose/report.php`; dit staat niet in het uploadpakket en wordt er dus niet door vervangen.

De webapp opent op **https://www.deheksenketel.com/dns-diagnose/app/index.html**. Dit adres werkt pas nadat de bestanden zijn geplaatst. PHP 8.1+ vereist voor hostingmetingen.

## iPhone en iPad

Download het `.mobileconfig`-bestand in Safari. Installeer via **Instellingen → Profiel gedownload**. Het icoon **Heksenketel Test** opent de webapp met DNS-vergelijkingen, bereikbaarheidscontrole, lokaal opgeslagen rapporten en automatische aanbieding voor e-mail naar info@deheksenketel.com. Het ongetekende profiel kan de eerdere websitesnelkoppeling vervangen.

## Meetbereik

Browsermetingen gaan rechtstreeks vanaf het toestel naar onafhankelijke DoH-diensten en naar de website. Provider-DNS-metingen lopen vanaf de hostingserver en zijn duidelijk als zodanig gelabeld. Een browser kan systeem-DNS, providernaam en VPN-status niet betrouwbaar uitlezen of rechtstreeks provider-DNS via UDP/TCP bevragen. De webapp is dus geen één-op-één vervanging van de native meetmogelijkheden.

Time-outs of andere IP-adressen bewijzen op zichzelf geen blokkade. De mailbevestiging betekent acceptatie voor verzending, geen gegarandeerde aflevering. Het rapportadres is intern vastgelegd en niet aanpasbaar in de gebruikersinterface.
