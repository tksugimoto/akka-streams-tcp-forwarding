# TCP 転送
ローカルの特定port宛のTCP接続を別 `host:port` に転送する

## 起動方法
```
sbt run
```

## 設定

|                    	| キー                       	| デフォルト値  	|
|--------------------	|----------------------------	|---------------	|
| ローカル bind host 	| tcp-forwarding.bind.host   	| 127.0.0.1     	|
| ローカル bind port 	| tcp-forwarding.bind.port   	| 8080          	|
| 転送先 host        	| tcp-forwarding.target.host 	| example.com   	|
| 転送先 port        	| tcp-forwarding.target.port 	| 80            	|

### 設定値を変更して起動する方法
`sbt` の直後に `-Dキー=値` 形式でオプションとして追記する。

#### 例
- ローカル bind port を `4567` に変更
- 転送先 host を `10.1.2.3` に変更
- 転送先 port を `3128` に変更

```
sbt -Dtcp-forwarding.bind.port=4567 -Dtcp-forwarding.target.host=10.1.2.3 -Dtcp-forwarding.target.port=3128 run
```
