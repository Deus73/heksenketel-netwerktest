# Controles webapp v1.3

Uitgevoerd op 19 oktober 2026 in een desktopbrowser met een lokale PHP-testhosting en nagebootst mailtransport. Dit verandert de live website niet en bevestigt geen aflevering in de echte mailbox.

- Appscherm geopend; geen invoerveld of instelbaar ontvangstpunt.
- Vier rechtstreekse browser-DoH-metingen (Cloudflare/Google, A/AAAA) beantwoorden de echte domeinnaam.
- Veertien server-DNS-metingen (zeven resolvers, A/AAAA) uitgevoerd en apart gelabeld.
- Bereikbaarheidscontrole uitgevoerd tegen de lokale websitefixture; dit is geen nieuwe meting van de live HTTPS-site.
- Rapport automatisch aangeboden aan het echte PHP-ontvangstscript met nagebootst mailtransport: aangenomen, wachtrij leeg.
- HTTP 503 voor rapportupload gesimuleerd: drie pogingen, rapport lokaal behouden, download- en herhaalknop beschikbaar.
- Fout opgeheven en herhaalknop gebruikt: rapport aangenomen, wachtrij weer leeg.
- Serviceworker actief; appbestanden gecachet.
- JavaScript-syntaxis, PWA-manifest en Apple XML-profiel gecontroleerd.
- Zeven PHP-DNS-protocolgevallen geslaagd (correct antwoord, foutcodes en ongeldige pakketten).
- Vier bestaande PHP-mailintegratietests geslaagd.

Geen fysieke iPhone/iPad-installatie, iOS-webclip-runtime of Safari-offlinegedrag getest. De live webapp vereist het uploaden van het pakket en een test op de echte hosting; uitgaande DNS-toegang kan daar verschillen.
