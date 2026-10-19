# Heksenketel Netwerktest voor iPhone en iPad

**[Download De-Heksenketel.mobileconfig](https://github.com/Deus73/heksenketel-netwerktest/releases/download/v1.3/De-Heksenketel.mobileconfig)**

Dit profiel voegt **Heksenketel Test** als icoon toe aan het beginscherm. Het icoon opent de webapp op:

**https://www.deheksenketel.com/dns-diagnose/app/index.html**

De webapp moet eerst op de hosting worden geplaatst; zie de [webapp-handleiding](../webapp/README.md). De webapp voert na een druk op de testknop browsermetingen en afzonderlijk gelabelde hostingmetingen uit en biedt het rapport automatisch aan voor e-mail. Het profiel zelf is uitsluitend de snelkoppeling en wijzigt geen DNS- of netwerkinstellingen.

## Installeren

1. Open de downloadlink in **Safari** op je iPhone of iPad.
2. Sta het downloaden van het configuratieprofiel toe als iOS daarom vraagt.
3. Open **Instellingen → Profiel gedownload**. Zie je die optie niet, ga dan naar **Algemeen → VPN en apparaatbeheer**.
4. Selecteer **Heksenketel Netwerktest — webapp**, tik op **Installeer** en bevestig met je toegangscode.
5. Open **Heksenketel Test** op het beginscherm. De pagina toont **Webapp · v1.3**.

Het profiel heeft dezelfde identificatie als het eerdere websiteprofiel. iOS kan daarom vragen de bestaande configuratie te vervangen. Het profiel is niet digitaal ondertekend; iOS vermeldt dit tijdens installatie. Beheerde toestellen kunnen installatie beperken. Er is geen Apple Developer-account nodig.

## Verwijderen

Ga naar **Instellingen → Algemeen → VPN en apparaatbeheer → Heksenketel Netwerktest — webapp → Verwijder profiel**. Het webclip-icoon is ook verwijderbaar. Rapporten van de webapp worden apart in de websiteopslag van de browser bewaard; het verwijderen van het profiel wist deze niet noodzakelijk.

## Verschil met de Android-app

Een browser kan niet rechtstreeks dezelfde systeem- en provider-DNS-metingen uitvoeren als een native Android-app. De webapp toont duidelijk welke metingen vanuit de browser komen en welke vanaf de hostingserver. Zie de [vergelijking](../webapp/README.md#welke-metingen-vinden-waar-plaats).

Het profiel bevat één verwijderbare webclip en geen VPN, apparaatbeheerinschrijving, certificaten of DNS-configuratie. De XML-property-list is gecontroleerd. Installatie op een fysieke iPhone/iPad is vanuit deze werkomgeving niet getest.
