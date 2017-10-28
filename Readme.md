# Microsoft Translator Hands on Lab

Microsoft Translator はテキスト(英語)からテキスト(日本語)の翻訳のほか、音声データ(wavファイル)からテキストへ翻訳などができます。本ハンズオンでは、テキストからテキストの翻訳、音声からテキストへの翻訳の２種類の翻訳の実装方法について紹介します。


## 1. Microsoft Translator text to text のハンズオン(JAX-RS)

テキストからテキストへの翻訳は、下記の手順に従い翻訳を行うことができます。

1. サブスクリプション・キーを元に下記 URL からアクセス・トークンを取得
[https://api.cognitive.microsoft.com/sts/v1.0/issueToken](https://api.cognitive.microsoft.com/sts/v1.0/issueToken)
2. アクセストークンを HTTP ヘッダを付加、クエリ・パラメータで翻訳対象のテキストを付加、Microsoft Translator の接続 URL に接続
[https://api.microsofttranslator.com/v2/http.svc/Translate](https://api.microsofttranslator.com/v2/http.svc/Translate)



```
package com.yoshio3.services;

import java.util.Optional;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.jackson.JacksonFeature;

/**
 * Translate English document to Japanese documents by using 
 * Microsoft Translator Text API.
 *
 * @author Yoshio Terada
 */
public class TranslatorTextServices {

    private final static String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private final static String AUTH_URL = "https://api.cognitive.microsoft.com/sts/v1.0/issueToken";
    private final static String TRANSLATOR_URL = "https://api.microsofttranslator.com/v2/http.svc/Translate";
    private final static String ERROR_MESSAGE = "正しく翻訳ができませんでした。";
    private final static String SUBSCRIPTION_KEY;

    static {
        SUBSCRIPTION_KEY = "******************************";
     }    
    
    /**
     * Get Access Token from Auth Server.
     *
     * The detail information to get the Auth Token is as follows.
     * http://docs.microsofttranslator.com/oauth-token.html
     *
     * Retrieve your authentication key from the Azure Admin screen. For example
     * accessKey look like following digit value
     * "a2436*********************9bc7e"
     *
     * @return {@code Optional<String>} if
     */
    public Optional<String> getAccessTokenForTranslator() {
        Client client = ClientBuilder.newBuilder()
                .register(JacksonFeature.class)
                .build();
        Entity<String> entity = Entity.entity("", MediaType.TEXT_PLAIN_TYPE);
        Response response = client.target(AUTH_URL)
                .request()
                .header(OCP_APIM_SUBSCRIPTION_KEY, SUBSCRIPTION_KEY)
                .post(entity);
        if (isRequestSuccess(response)) {
            return Optional.of(response.readEntity(String.class));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Translate from English to Japanese.
     *
     * The detail information to translate the document is as follows.
     * http://docs.microsofttranslator.com/text-translate.html
     *
     *
     * @param englishText The text value which you would like to translate.
     * @param accessToken The Key value for authentication
     *
     * @return {@code String} Translated Japanese String
     */
    public String translateEnglish(String englishText, String accessToken) {
        Client client = ClientBuilder.newBuilder()
                .register(JacksonFeature.class)
                .build();

        Response response = client.target(TRANSLATOR_URL)
                .queryParam("text", englishText)
                .queryParam("to", "ja")
                .queryParam("contentType", MediaType.TEXT_PLAIN)
                .request()
                .header("Accept", MediaType.APPLICATION_XML)
                .header("Authorization", "Bearer " + accessToken)
                .get();
        if (isRequestSuccess(response)) {
            return response.readEntity(String.class);
        } else {
            return ERROR_MESSAGE;
        }
    }

    private boolean isRequestSuccess(Response response) {
        Response.StatusType statusInfo = response.getStatusInfo();
        Response.Status.Family family = statusInfo.getFamily();
        return family != null && family == Response.Status.Family.SUCCESSFUL;
    }
}
```

上記の実装を利用し、ファイルに記載されている、英語のドキュメント (/tmp/english-document.txt) を翻訳するプログラムを書きに記載します。

```
package com.yoshio3.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String... args) throws IOException{
        Path path = Paths.get("/tmp/english-document.txt");
        List<String> readAllLines = Files.readAllLines(path, StandardCharsets.UTF_8);
        readAllLines.stream().forEach(line -> {
            TranslatorTextServices translator = new TranslatorTextServices();
            Optional<String> accessTokenForTranslator = translator.getAccessTokenForTranslator();
            accessTokenForTranslator.ifPresent(token -> {
                String translateEnglish = translator.translateEnglish(line, token);
                System.out.println(translateEnglish);
            });
        });
    }
}
```


## 2. WebSocket Translator Speech to Text のハンズオン

### 2.1 : WebSocket Client 基礎の実装

WebSocket とは
WebSocket（ウェブソケット）は、インターネットの標準化団体であるW3CとIETFが定める通信規格の一つで、W3C が APIを、そしてプロトコルは IETFが規定しています。WebSocket プロトコルは、HTTP を拡張し、双方向で全二重の通信が可能です。

　WebSocket の詳細を説明するまえに、かんたんに、ブラウザ上でWebページを表示するための仕組みを説明します。通常、ブラウザ(HTTP クライアントともいいます)から、Webサーバと呼ぶWebページを管理するサーバに対して接続します。ブラウザには、Internet ExplorerやMicrosoft Edge (Windows 10に含まれる新しいブラウザ)、Firrox、Safariのようなさまざまな製品があります。
　Webサーバに接続するためには、ブラウザ上でhttp://で始まるURLをアドレス欄に入力し、目的のサーバに対して接続します。

![](https://c1.staticflickr.com/5/4457/36957065094_5e3d95f432_z.jpg)

通常の、ブラウザとWebサーバとのやりとりは、必ずブラウザ(HTTPクライアント)側からリクエストを送信し、リクエストに対応するレスポンスを受け取ります。この方法を利用したデータ交換は伝統的なブラウザとWebサーバ間のデータ交換方法で、今後も多くの場面でこの方法が利用されます。

　上記の方法は多くの場面でうまく動作しますが、必ずブラウザ側からリクエストを送信しなければ、結果を得られないという課題がありました。これにより、どのような問題が発生するかを考えてみましょう。例えば、コンサート・チケットの予約サイトに対してアクセスし、人気のあるアーティストのコンサートのチケットを予約する場合を考えてみましょう。人気のあるアーティストのチケットの場合、チケットは争奪戦になります。そして刻一刻、もっというならば秒単位でチケットの残数は減っていくでしょう。このような場合、あと何枚チケットが残っているかを確認するために、今まではブラウザを強制的にリロード（再読み込み）するか、数秒ごとに再読み込みするためのHTMLのコードを埋め込む必要がありました。画面全体を再読み込みするのはとても負荷の高い（ネットワーク帯域や Webサーバに対する負荷）作業になります。実際に、数百人〜数千人の人が同じWebサーバに対して定期的に再読み込みをした場合、Webサーバに対しては非常に多くの負荷がかかり、不要なデータがネットワーク回線上に流れることになります。


![](https://c1.staticflickr.com/5/4480/36957065154_82f47dfaea_z.jpg)


そこで、AjaxというJavaScriptの技術を利用し、画面全体ではなく、必要なデータの一部（この場合は、チケットの残数）だけを取得する方法が考えられました。しかし、この方法も、上記と同様にブラウザ側から定期的にWebサーバに対して確認を行い、状況を取得します（polling）。この時、たとえデータに変化がない場合でもデータの送受信は発生します。

　こうした問題を解消するために、Webサーバ側でなんらかのイベントが発生した際に、クライアントに情報を配信するためにReverse Ajax(Comet)という技術がでてきました。Reverse Ajax (Comet)では２種類の方法を選択することができます。一つ目の方法は、ブラウザと Webサーバを常時接続し、サーバ側でなんらかのイベントが発生した際に、ブラウザに通知するStreamingという方法、そして、もう一つはLong-Pollingという方法で、常時接続ではなく、サーバ側でなんらかのイベントが発生した際にクライアントに通知し、接続を一旦切断したのち、改めてWebサーバに対して再接続する方法のいずれかの方法を利用できました。

![](https://c1.staticflickr.com/5/4495/36957065194_55672084a6_z.jpg)

かつて、Reverse Ajax(Comet)を利用したWebサーバ側からの通知に注目を浴びましたが、実際にReverse Ajaxを利用した場合、大量のアクセスに対して処理をさばききれない問題が発生しました。理由はブラウザとWebサーバ間でのやりとりはHTTPというプロトコルを利用しているため、HTTPのルールに乗っ取った余分なデータ(HTTPヘッダ)の情報交換が必要なためでした。また、標準的な技術ではなかったため、Webサーバごとに実装をかえなければならない、もしくはWebサーバが用意するAPIを用いて実装しなければならないといったように、アプリケーションを実装する際にも課題を抱えていました。
　こうした過去の経験を踏まえ、新たにブラウザ(HTTPクライアント)とWebサーバ(HTTPサーバ)間で、双方向かつ全二重に通信ができるHTTPを拡張した新しい通信プロトコルの策定がはじまりました。WebSocketはインターネットの標準化団体であるW3CとIETFが定める通信規格の一つです。これを利用すると、Webサーバ側で発生したなんらかのイベントに応じて、クライアントであるブラウザに対して情報を通知することができるほか、WebブラウザからWebサーバに対して情報を送信することも可能です。
　以降のハンズオン・ラボではWebSocketを利用した、サンプルのチャット・アプリケーションの構築を行います。WebSocketを利用しないチャット・アプリケーションの場合、ブラウザを再読み込みしなければ他の参加者が記入したメッセージを確認することはできませんでした。しかしWebSocketのチャット・アプリケーションの場合は、ブラウザを再読み込みしなくても、他の参加者が記入したメッセージを確認することができます。
　WebSocketは、チャット・アプリケーションのようなアプリケーションだけでなく、双方向にデータの交換が必要な場合に幅広く利用できるため、アイディア次第ではとても面白いアプリケーションを作成することができます。

![](https://c1.staticflickr.com/5/4445/36996666213_77eeb79a96_z.jpg)

Microsoft Translator は WebSocket を利用して、音声データやテキストテータの送受信を行うことができます。まずは、WebSocket のクライアントアプリケーションを作成し、WebSocket の振る舞いについて理解しましょう。

WebSocket クライアントプログラムの作成
pom.xml に下記を記述

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.yoshio3</groupId>
    <artifactId>MS-Translator-Speech-HoL</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>javax.websocket</groupId>
            <artifactId>javax.websocket-client-api</artifactId>
            <version>1.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.tyrus</groupId>
            <artifactId>tyrus-client</artifactId>
            <version>1.13.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.tyrus</groupId>
            <artifactId>tyrus-container-grizzly-client</artifactId>
            <version>1.13.1</version>
        </dependency>
        <dependency>
            <groupId>javax.json</groupId>
            <artifactId>javax.json-api</artifactId>
            <version>1.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish</groupId>
            <artifactId>javax.json</artifactId>
            <version>1.1</version>
        </dependency>
    </dependencies>
</project>
```

ここで、かんたんな WebSocket Client Endpoint プログラムを作成します。このプログラムを通じて、WebSocket サーバと通信するプログラムを実装します。WebSocket のクライアント・エンドポイントを実装するためには、@ClientEndpoint のアノテーションを付加したクラスを作成します。

また、各メソッドに下記のアノテーションを付加し、それぞれの処理を実装します。

|アノテーション |アノテーションの意味|
|---|---|
|@OnOpen| WebSocket サーバとの接続時の処理を実装|
|@OnClose| WebSocket サーバとの切断時の処理を実装|
|@OnError| エラー発生時の実装を記述|
|@OnMessage| WebSokcet サーバからメッセージを受信した際の処理の記述|

今回は、実装を簡単にするため、メッセージを受信時「メッセージありがとうございます： message」の文言を表示するように実装します。


```
package com.yoshio3;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;


@ClientEndpoint
public class TranslatorWebSockerClientEndpoint {

    private Session session;
    private static final Logger LOGGER = Logger.getLogger(TranslatorWebSockerClientEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) throws IOException, InterruptedException, DeploymentException {
        System.out.println("接続完了");
    }

    @OnClose
    public void onClose(CloseReason reason) {
        System.out.println("切断");
    }

    @OnError
    public void onError(Throwable throwable) {
        this.session = null;
        LOGGER.log(Level.SEVERE, null, throwable);
    }
    
    @OnMessage
    public void onMessage(String message) throws IOException {
        System.out.println("メッセージありがとうございます：" + message);
    }    
}
```

次に、この WebSocket クライアント・エンドポイントを利用できるよう Main クラスを実装します。Main クラスでは、WebSocket クライアントを新規スレッドを作成し起動します。
今回、WebSocket サーバの接続先を、microsoftTranslatorURI に代入します。


```
package com.yoshio3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 *
 * @author yoterada
 */
public class Main {

    public static void main(String... args) {

        try {
            String microsoftTranslatorURI = "ws://localhost:8080/SampleWebSocket-Server/endpoint";
            URI serverEndpointUri = new URI(microsoftTranslatorURI);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(System.in))) {

                    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                    Session translatorSession = container.connectToServer(TranslatorWebSockerClientEndpoint.class, serverEndpointUri);

                    translatorSession.getBasicRemote().sendText("こんにちは！！");
                    String line;
                    while ((line = stdReader.readLine()) != null) {
                        if (line.equals("exit")) {
                            translatorSession.close();
                            executor.shutdown();
                            break;
                        }
                    }

                } catch (DeploymentException | IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

        } catch (URISyntaxException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
```

### 2.2 : Microsoft Translator の接続 URL の作成

Microsoft Translator へ接続するために URL を作成します。from で元の言語を指定し、to で翻訳する言語を指定します。たとえば英語から日本語への翻訳を行いたい場合の WebSocket 接続 URL は下記のようになります。

```
wss://dev.microsofttranslator.com/speech/translate?
features=partial&from=en-US&to=ja-JP&api-version=1.0;
```

現在 (2017年10月時点) 対応している言語は下記です。


|Supported language |values|
|---|---|
|Arabic|ar-EG|
|German| de-DE|
|Spanish| es-ES|
|English| en-US|
|French| fr-FR|
|Italian| it-IT|
|Japanese| ja-JP|
|Portuguese| pt-BR|
|Russian| ru-RU|
|Chinese Simplified| zh-CN|
|Chinese Traditional| zh-TW|


URL を指定する際、features パラメータを設定すると、追加情報を取得することができます。例えば最終的な翻訳結果だけではなく、部分的な翻訳結果を取得したい場合、features=partial を取得します。また、翻訳された結果を音声化したい場合は、features=TextToSpeech を付加します。 （今回の実装では音声化までは不要なので、TextToSpeech は省略しています。）

```
public class Main {

    private static final String TRANSLATOR_WEBSOCKET_ENDPOINT = "wss://dev.microsofttranslator.com/speech/translate?features=partial&";

    private final static String FROM = "ja-JP";
    private final static String TO = "en-US";

    public static void main(String... args) throws URISyntaxException, IOException {
        Main main = new Main();
        StringBuilder strBuilder = new StringBuilder();
        String microsoftTranslatorURI = strBuilder.append(TRANSLATOR_WEBSOCKET_ENDPOINT)
                .append("from=")
                .append(FROM)
                .append("&to=")
                .append(TO)
                .append("&api-version=1.0")
                .toString();
```

### 2.3 : WebSocket ヘッダの作成

Microsoft Translator の URL に対して接続するためには、ヘッダにアクセス・トークンと、X-ClientTraceIdを付加し接続する必要があります。

Authorization: Bearer {access token}  
X-ClientTraceId: {UUID}

JSR-356 の WebSocket クライアント実装の場合、ClientEndpointConfig.Configurator を継承したクラスを作成し、その中でヘッダを追加する事ができます。

```
package com.yoshio3.websocket.config;

import com.yoshio3.auth.AuthTokenService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.HandshakeResponse;


public class TranslatorWebSocketClientEndpointConfigurator extends ClientEndpointConfig.Configurator{
    private static final Logger LOGGER = Logger.getLogger(TranslatorWebSocketClientEndpointConfigurat

    /**
     * Create WebSocket Header
     * 
     * @param headers 
     */
    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        AuthTokenService tokenService = new AuthTokenService();
        tokenService.getAccessTokenForTranslator().ifPresent(accessToken -> {
            headers.put("Authorization", Arrays.asList("Bearer " + accessToken));
        });
        String uuid = UUID.randomUUID().toString();
        headers.put("X-ClientTraceId", Arrays.asList(uuid));
        LOGGER.log(Level.INFO, "X-ClientTraceId: {0}", uuid);
    }

    @Override
    public void afterResponse(HandshakeResponse hr) {
        ;
    }    
}
```

ここで、認証を行うための Access Token を取得する必要があります。Access Token を取得するためには、Token API V1.0 を利用します。 たとえば、curl コマンドを利用してアクセス・トークンを取得する場合は、下記のコマンドを実行し取得可能です。
(具体例：Authentication Token API for Microsoft Cognitive Services Translator API )

```
$ curl -X POST 
  "https://api.cognitive.microsoft.com/sts/v1.0/issueToken" 
  -H "Ocp-Apim-Subscription-Key: {SUBSCRIPTION_KEY}" 
  --data-ascii ""
```

ここで、HTTP ヘッダに付加する "Ocp-Apim-Subscription-Key: "の "{SUBSCRIPTION_KEY}" の値は、Azure の管理ポータルから取得します。Microsoft Translator 用のサービスを作成した際に自動的に生成されるのでこちらから取得してください。

![](https://camo.githubusercontent.com/bb3faca91436eb9d8bd342d1d6a12ee7d0831c15/68747470733a2f2f63312e737461746963666c69636b722e636f6d2f352f343338352f33363232393037303937305f613734363131376639622e6a7067)

"RESOURCE MANAGEMENT" より "Kyes" を選択してください。選択すると下記の画面が表示されます。

![](https://camo.githubusercontent.com/472d75c9a886ef91bf071994c8d4ca7aa3c0f755/68747470733a2f2f63312e737461746963666c69636b722e636f6d2f352f343336382f33363438373730383436315f306465306234616537392e6a7067)

上記の例では、curl コマンドを使用してアクセス・トークンを取得しましたが、これを Java プログラムから取得するために、JAX-RS (RESTful WebService for Java) を利用します。JAX-RS を利用できるように pom.xml ファイルに下記の dependency を追記してください。

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.yoshio3</groupId>
    <artifactId>MS-Translator-Speech-HoL</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>javax.websocket</groupId>
            <artifactId>javax.websocket-client-api</artifactId>
            <version>1.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.tyrus</groupId>
            <artifactId>tyrus-client</artifactId>
            <version>1.13.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.tyrus</groupId>
            <artifactId>tyrus-container-grizzly-client</artifactId>
            <version>1.13.1</version>
        </dependency>
        <dependency>
            <groupId>javax.json</groupId>
            <artifactId>javax.json-api</artifactId>
            <version>1.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish</groupId>
            <artifactId>javax.json</artifactId>
            <version>1.1</version>
        </dependency>

        <dependency>
            <groupId>javax.ws.rs</groupId>
            <artifactId>javax.ws.rs-api</artifactId>
            <version>2.0</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jersey.core</groupId>
            <artifactId>jersey-client</artifactId>
            <version>2.25.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jersey.media</groupId>
            <artifactId>jersey-media-json-jackson</artifactId>
            <version>2.25.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jersey.ext.rx</groupId>
            <artifactId>jersey-rx-client-java8</artifactId>
            <version>2.25.1</version>
        </dependency>
    </dependencies>
</project>
```

上記の設定で、JAX-RS が利用できるようになりました。AuthTokenService クラスを作成し、[https://api.cognitive.microsoft.com/sts/v1.0/issueToken](https://api.cognitive.microsoft.com/sts/v1.0/issueToken) に対して POST をリクエストしアクセストークンを取得してください。この際ヘッダに、OCP_APIM_SUBSCRIPTION_KEY ヘッダを付加してください。

```
package com.yoshio3.auth;

import java.util.Optional;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.jackson.JacksonFeature;


public class AuthTokenService {

    private final static String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private final static String AUTH_URL = "https://api.cognitive.microsoft.com/sts/v1.0/issueToken";
    private final static String SUBSCRIPTION_KEY = "a2436*********************9bc7e";

    /**
     * Get Access Token from Auth Server.
     *
     * The detail information to get the Auth Token is as follows.
     * http://docs.microsofttranslator.com/oauth-token.html
     *
     * Retrieve your authentication key from the Azure Admin screen. For example
     * accessKey look like following digit value
     * "a2436*********************9bc7e"
     *
     * @return {@code Optional<String>} if
     */
    public Optional<String> getAccessTokenForTranslator() {
        Client client = ClientBuilder.newBuilder()
                .register(JacksonFeature.class)
                .build();
        Entity<String> entity = Entity.entity("", MediaType.TEXT_PLAIN_TYPE);
        Response response = client.target(AUTH_URL)
                .request()
                .header(OCP_APIM_SUBSCRIPTION_KEY, SUBSCRIPTION_KEY)
                .post(entity);
        if (isRequestSuccess(response)) {
            return Optional.of(response.readEntity(String.class));
        } else {
            return Optional.empty();
        }
    }

    private boolean isRequestSuccess(Response response) {
        Response.StatusType statusInfo = response.getStatusInfo();
        Response.Status.Family family = statusInfo.getFamily();
        return family != null && family == Response.Status.Family.SUCCESSFUL;
    }
}
```

これで、プログラム上からアクセス・トークンを取得する事ができるようになりました。次に、WebSocket のクライアントが WebSocket サーバと接続をするために、WebSocket クライアントのヘッダ情報を作成します。WebSocket クライアントのヘッダを作成するために、ClientEndpointConfig.Configurator を継承したTranslatorWebSocketClientEndpointConfigurator クラスを作成してください。
この中で、beforeRequest() メソッド内で、アクセストークンのヘッダと X-ClientTraceId ヘッダを付加します。

```
package com.yoshio3.websocket.config;

import com.yoshio3.auth.AuthTokenService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.HandshakeResponse;

public class TranslatorWebSocketClientEndpointConfigurator extends ClientEndpointConfig.Configurator{
    private static final Logger LOGGER = Logger.getLogger(TranslatorWebSocketClientEndpointConfigurator.class.getName());

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        AuthTokenService tokenService = new AuthTokenService();
        tokenService.getAccessTokenForTranslator().ifPresent(accessToken -> {
            headers.put("Authorization", Arrays.asList("Bearer " + accessToken));
        });
        String uuid = UUID.randomUUID().toString();
        headers.put("X-ClientTraceId", Arrays.asList(uuid));
    }

    @Override
    public void afterResponse(HandshakeResponse hr) {
        ;
    }    
}
```

上記で、WebSocket ヘッダを作成する事ができました。作成したヘッダを利用できるように、Client Endpoint のクラス定義を下記のように修正してください。

```
@ClientEndpoint(
        configurator = TranslatorWebSocketClientEndpointConfigurator.class)
public class TranslatorWebSockerClientEndpoint {
```


以上で、Microsoft Translator と接続をすることはできるようになります。


### 2.3 : 音声データの送信
Microsoft Translator に対して WebSocket で接続し音声のバイナリデータを送信すると翻訳 (音声→テキスト) ができるようになります。

テキストデータの送信は、下記のように sendText() メソッドを利用し送信していました。

```
translatorSession.getBasicRemote().sendText("こんにちは！！");
```

音声データのようなバイナリデータの送信は、sendBinary() メソッドを利用します。

```
translatorSession.getBasicRemote().sendBinary(ByteBuffer.wrap(createWAVHeaderForInfinite16KMonoSound));
```


送信する音声データは、 wav 形式のデータを送信するのですが、送信する音声データは下記のフォーマットに従う必要があります。

|Item| Value|
|---|---|
|Sound source (number of channels)| mono|
|Sampling rate| 16 KHz|
|Format Size| 16bit signed PCM|

上記は、[リファレンス・ガイド](http://docs.microsofttranslator.com/speech-translate.html) にも記載がありますのでご注意ください。 特に、最近の PC ハードウェアで音声を録音すると、より高音質のデータが保存されます（例：ステレオ、サンプリングレート：44.100 kHz、フォーマット・サイズが 16bit）。その場合は、フォーマットを変換して Microsoft Translator に送信してください。　
　
また、テキストデータに比べ音声データのサイズは大きくなります。そして、Microsoft Translator 側も１回に受信できるデータ・サイズに制限を設けています。一方で、連続した音声データをストリーミング・データとして常に流したい場合もあります。

このように、ストリーミングで送信したい場合、***WAV ヘッダ情報のサイズを 0 にする事で、一定サイズの音波データ (PCM) をチャンクとして、繰り返し送信できる***ようになります 

上記のように WAV ヘッダ情報のサイズを 0 に変更するために、今回 Java の Sound API を利用します。

1. createWAVHeaderForInfinite16KMonoSound() メソッド： サイズ 0 の WAV ヘッダを作成します。  
2. trimWAVHeader(byte[] origin) メソッド： WAV ヘッダ部分を取り除き PCM の音波データ部分だけのデータを作成します。  
3. convertPCMDataFrom41KStereoTo16KMonoralSound() メソッド： サンプリングレート(44.1KHz->16KHz)を変更したデータを取得します。  

WAV フォーマットの詳細：https://en.wikipedia.org/wiki/WAV

```
package com.yoshio3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class SoundUtil {

    private final static int FORMAT_CHUNK_SIZE = 16;
    private final static int FORMAT = 1;
    private final static int CHANNEL = 1; //mono:1 stereo:2
    private final static int SAMPLERATE = 16000;
    private final static int BIT_PER_SAMPLE = 16;
    private final static int WAV_HEADER_SIZE = 44;
    private final static int BYTE_BUFFER = 1024;

    /**
     *
     * @param soundBinary
     * @return
     * @throws UnsupportedAudioFileException
     * @throws IOException
     */
    public byte[] convertPCMDataFrom41KStereoTo16KMonoralSound(byte[] soundBinary) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(soundBinary);
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais);
        return convertedByte41KStereoTo16KMonoralSound(sourceStream);
    }

    public byte[] createWAVHeaderForInfinite16KMonoSound() throws IOException {
        int chunk = 0;
        int dataSize = 0;
        // Please refer to WAV format. Following is Japanese explanation.
        // http://www.wdic.org/w/TECH/WAV
        // http://docs.microsofttranslator.com/speech-translate.html
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        bOut.write("RIFF".getBytes()); // RIFF Header
        bOut.write(getIntByteArray(chunk)); //Total File Size(datasize + 44) - 8;
        bOut.write("WAVE".getBytes()); //WAVE Header
        bOut.write("fmt ".getBytes()); //FORMAT Header
        bOut.write(getIntByteArray(FORMAT_CHUNK_SIZE));
        bOut.write(getFloatByteArray(FORMAT));
        bOut.write(getFloatByteArray(CHANNEL));
        bOut.write(getIntByteArray(SAMPLERATE));
        int ByteRate = SAMPLERATE * 2 * CHANNEL; // Sampling * 2byte * Channel
        bOut.write(getIntByteArray(ByteRate));
        int BlockSize = (int) CHANNEL * BIT_PER_SAMPLE / 8;
        bOut.write(getFloatByteArray(BlockSize));
        bOut.write(getFloatByteArray(BIT_PER_SAMPLE));
        bOut.write("data".getBytes()); // Data Header
        bOut.write(getIntByteArray(dataSize));

        return bOut.toByteArray();
    }    

    public boolean is16KMonoralSound(byte[] soundData) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(soundData);
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bais);
        long frameLength = sourceStream.getFrameLength();
        AudioFormat format = sourceStream.getFormat();

        int sampleSizeInBits = format.getSampleSizeInBits();
        float frameRate = format.getFrameRate();
        int channels = format.getChannels();

        return sampleSizeInBits == BIT_PER_SAMPLE
                && frameRate == SAMPLERATE
                && channels == CHANNEL;
    }
    
    public byte[] trimWAVHeader(byte[] origin){       
        return Arrays.copyOfRange(origin, WAV_HEADER_SIZE ,origin.length - WAV_HEADER_SIZE);
    }
    
    
    private byte[] convertedByte41KStereoTo16KMonoralSound(AudioInputStream sourceStream) throws UnsupportedAudioFileException, IOException {
        AudioFormat sourceFormat = sourceStream.getFormat();
        AudioFormat targetFormat = new AudioFormat(SAMPLERATE, BIT_PER_SAMPLE, CHANNEL, true, false);
        byte[] soundData;
        if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream)) {
                soundData = getByteFromAudioInputStream(audioInputStream);
            }
            return createWAVBinaryDataFor16KMonoSound(soundData);
        } else {
            return getByteFromAudioInputStream(sourceStream);
        }
    }

    private byte[] getByteFromAudioInputStream(AudioInputStream audioInputStream) throws IOException {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[BYTE_BUFFER];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(bytes, 0, bytes.length)) != -1) {
                bout.write(bytes, 0, bytesRead);
            }
            return bout.toByteArray();
        }
    }    

    private byte[] createWAVBinaryDataFor16KMonoSound(byte[] soundData) throws IOException {
        int totalFileSize = soundData.length + WAV_HEADER_SIZE;
        int chunk = totalFileSize - 8;
        int dataSize = totalFileSize - 126;

        // Please refer to WAV format. Following is Japanese explanation.
        // http://www.wdic.org/w/TECH/WAV
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        bOut.write("RIFF".getBytes()); // RIFF Header
        bOut.write(getIntByteArray(chunk)); //Total File Size(datasize + 44) - 8;
        bOut.write("WAVE".getBytes()); //WAVE Header
        bOut.write("fmt ".getBytes()); //FORMAT Header
        bOut.write(getIntByteArray(FORMAT_CHUNK_SIZE));
        bOut.write(getFloatByteArray(FORMAT));
        bOut.write(getFloatByteArray(CHANNEL));
        bOut.write(getIntByteArray(SAMPLERATE));
        int ByteRate = SAMPLERATE * 2 * CHANNEL; // Sampling * 2byte * Channel
        bOut.write(getIntByteArray(ByteRate));
        int BlockSize = (int) CHANNEL * BIT_PER_SAMPLE / 8;
        bOut.write(getFloatByteArray(BlockSize));
        bOut.write(getFloatByteArray(BIT_PER_SAMPLE));
        bOut.write("data".getBytes()); // Data Header
        bOut.write(getIntByteArray(dataSize));
        bOut.write(soundData);
        return bOut.toByteArray();
    }
    
    private byte[] getIntByteArray(int intValue) {
        byte[] array = ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(intValue).array();
        return array;
    }

    private byte[] getFloatByteArray(int data) {
        byte[] array = ByteBuffer
                .allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putChar((char) data)
                .array();
        return array;
    }
}    
```


上記の音声ユーティリティを使用して、下記の流れで処理を記載します。

1. WAV 音声データの取得
2. サイズを 0 に設定した WAV ヘッダのみ Microsoft Translator へ送信
3. オリジナル音声データの低品質化(44.1KHz->16Khz, Stereo->Monoral)
4. オリジナル音声データより WAV ヘッダ部分を削除
5. 音声データを固定長のバイト配列に分割(チャンク・データの作成)
6. 分割したバイト配列を順次 Microsoft Translator へ配信


上記の処理を Main クラス内に実装します。


```
package com.yoshio3;

import com.yoshio3.sound.SoundUtil;
import com.yoshio3.websocket.TranslatorWebSockerClientEndpoint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 *
 * @author yoterada
 */
public class Main {

    private static final String TRANSLATOR_WEBSOCKET_ENDPOINT = "wss://dev.microsofttranslator.com/speech/translate?features=partial";

    private final static String FROM = "ja-JP";
    private final static String TO = "en-US";

    public static void main(String... args) {
        try {
            Main main = new Main();
            StringBuilder strBuilder = new StringBuilder();
            String microsoftTranslatorURI = strBuilder.append(TRANSLATOR_WEBSOCKET_ENDPOINT)
                    .append("&from=")
                    .append(FROM)
                    .append("&to=")
                    .append(TO)
                    .append("&api-version=1.0")
                    .toString();

            URI serverEndpointUri = new URI(microsoftTranslatorURI);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                main.connectAndSendData(serverEndpointUri);

                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    //https://www.webrtc-experiment.com/RecordRTC/ で音声データ作成
                    while ((line = stdReader.readLine()) != null) {
                        if (line.equals("exit")) {
                            executor.shutdown();
                            System.exit(0);
                            break;
                        }
                    }
                } catch (IOException ioe) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ioe);
                }
            });
        } catch (URISyntaxException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void connectAndSendData(URI serverEndpointUri) {
        try {

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            Session translatorSession = container.connectToServer(TranslatorWebSockerClientEndpoint.class, serverEndpointUri);

            sendSoundHeader(translatorSession);
            sendSoundData("/tmp/sound.wav", translatorSession);

        } catch (UnsupportedAudioFileException | IOException | DeploymentException ex) {

            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    // In order to send the sound data as Stream
    // Size of 0 Header, I created.
    private void sendSoundHeader(Session session) throws IOException {
        SoundUtil soundUtil = new SoundUtil();
        byte[] createWAVHeaderForInfinite16KMonoSound = soundUtil.createWAVHeaderForInfinite16KMonoSound();
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(createWAVHeaderForInfinite16KMonoSound));
    }

    private void sendSoundData(String fileName, Session session) throws IOException, UnsupportedAudioFileException {
        Path path = Paths.get(fileName);
        byte[] readAllBytes = Files.readAllBytes(path);

        SoundUtil soundUtil = new SoundUtil();
        //44.1KHz->16Khz, Stereo->Monoral 
        byte[] convertedSound = soundUtil.convertPCMDataFrom41KStereoTo16KMonoralSound(readAllBytes);
        //Remove WAVE Header
        byte[] trimedHeader = soundUtil.trimWAVHeader(convertedSound);
        //Create Chunk Data
        List<byte[]> divided = divideArray(trimedHeader, 4096);
        //Send chunk sound data to Microsoft Translator
        divided.stream().forEachOrdered(bytes -> {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(bytes));
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private List<byte[]> divideArray(byte[] source, int chunksize) {
        List<byte[]> result = new ArrayList<>();
        int start = 0;
        while (start < source.length) {
            int end = Math.min(source.length, start + chunksize);
            result.add(Arrays.copyOfRange(source, start, end));
            start += chunksize;
        }
        return result;
    }
}
```

上記の Main クラスの中で connectAndSendData() メソッド内で、音声データを実際に送信しています。前述したように、まずは WAV ヘッダ情報を送信し、その後で PCM の音声データだけをチャンクデータとして分割し送信しています。

プログラムの実装が完了したのち、音声データを作成して保存してください。例えば、下記の URL にアクセスするとブラウザから音声データを作成し、ローカルに保存する事ができます。

[https://www.webrtc-experiment.com/RecordRTC/](https://www.webrtc-experiment.com/RecordRTC/)
	
上記サイトで WAV の音声データを作成したのち、/tmp/sound.wav に保存してください。
最後に、Main プログラムを実行してください、すると下記のようなメッセージがコンソールに表示されます。

```
onOpen() is called
Oct 13, 2017 6:19:12 PM com.yoshio3.websocket.TranslatorWebSockerClientEndpoint onOpen
情報: MS-Translator Connect
onMessage() is called
Oct 13, 2017 6:19:14 PM com.yoshio3.websocket.TranslatorWebSockerClientEndpoint onMessage
情報: MS-Translator : {"type":"partial","id":"0.5","recognition":"このプログラムは。","translation":"This program."} 
onMessage() is called
Oct 13, 2017 6:19:15 PM com.yoshio3.websocket.TranslatorWebSockerClientEndpoint onMessage
情報: MS-Translator : {"type":"partial","id":"0.11","recognition":"このプログラムは、リアルタイム翻訳アプリ。","translation":"This program is a real-time translation app."} 
onMessage() is called
Oct 13, 2017 6:19:15 PM com.yoshio3.websocket.TranslatorWebSockerClientEndpoint onMessage
情報: MS-Translator : {"type":"final","id":"0","recognition":"このプログラムは、リアルタイム翻訳アプリケーションです。","translation":"This program is a real-time translation application."} 
exit
------------------------------------------------------------------------
BUILD SUCCESS
------------------------------------------------------------------------
Total time: 16.712 s
Finished at: 2017-10-13T18:19:23+09:00
Final Memory: 12M/309M
------------------------------------------------------------------------
```
