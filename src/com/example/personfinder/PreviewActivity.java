package com.example.personfinder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PreviewActivity extends Activity {

	/** プログレスダイアログ */
    private static ProgressDialog waitDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);

		//Intentを取得
		Intent intent = getIntent();
		//intentから指定キーの文字列を取得する
		Uri data = intent.getData();
		// 画像を設定
		ImageView imageView = (ImageView)findViewById(R.id.imageView1);
		imageView.setImageURI(data);

		// リソースIDから画像のビットマップを取得
		Resources res = getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, R.id.imageView1);
		// ビットマップをImageViewに表示させる
		imageView = (ImageView)findViewById(R.id.imageView1);
		imageView.setImageBitmap(bmp);
		// ビットマップをbyte配列に変換する
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		// compressメソッドの第2引数はcompressorへのヒント用に0-100の値を入れる
		// 0は画像サイズが小さいことを示し、100は大きいことを示す
		bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream);
		byte[] bytes = stream.toByteArray();
		// byte配列をBase64(String)に変換する
		// 第2引数にBase64.NO_WRAPを指定すると一定間隔で入ってしまう改行が消せる
		String strBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

		// インスタンス作成
        waitDialog = new ProgressDialog(this);
        // タイトル設定
        waitDialog.setTitle("処理中...");
        // メッセージ設定
        waitDialog.setMessage("Please wait...");
        // スタイル設定 スピナー
        waitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        // キャンセル可能か(バックキーでキャンセル）
        waitDialog.setCancelable(false);
        // ダイアログ表示
        waitDialog.show();


		// HttpリクエストPOSTメソッドの実装
		//HttpClient httpClient = new DefaultHttpClient();
		/*
        CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			HttpPost postMethod = new HttpPost("http://192.168.100.87:8080/");
			List <NameValuePair> nvp = new ArrayList<NameValuePair>();
			nvp.add(new BasicNameValuePair("image", strBase64));
			postMethod.setEntity(new UrlEncodedFormEntity(nvp));
			CloseableHttpResponse response = httpClient.execute(postMethod);
			InputStream content;
			try {
				System.out.println(response.getStatusLine());
				HttpEntity entity = response.getEntity();
                // do something useful with the response body
                // and ensure it is fully consumed

				// レスポンスデータ(JSON)を取得
				content = entity.getContent();
				JSONObject json = new JSONObject(content);

				// ビットマップに色付けする
				findPerson(json);

                EntityUtils.consume(entity);
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				content.close();
				response.close();
		        waitDialog.dismiss();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			httpClient.close();
		}
		*/

		String response = postByOkHttp(strBase64);
		waitDialog.dismiss();
		try {
			JSONObject json = new JSONObject(response);
			// ビットマップに色付けする
			findPerson(json, bmp);
		} catch (JSONException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}


	}

	/**
	 * 計算サーバーのレスポンスを基に人物領域をセグメンテーション
	 * @param json
	 */
	private void findPerson(JSONObject json, Bitmap bmp) {
		// jsonで色付け
        Bitmap outBitMap = bmp.copy(Bitmap.Config.ARGB_8888, true);

        int width = outBitMap.getWidth();
        int height = outBitMap.getHeight();
        int pixels[] = new int[width * height];
        outBitMap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
            	// アノテーション結果をもらう(0か15にラベリングされているはず)
            	int labelData = 0;
				try {
					labelData = Integer.parseInt(json.getString(String.valueOf(x + y * width)));
				} catch (NumberFormatException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
                int pixelColor = pixels[x + y * width];
                int R = (Color.red(pixelColor) + 192 > 255) ? 255 : Color.red(pixelColor) + 192;
                int G = (Color.green(pixelColor) + 128 > 255) ? 255 : Color.green(pixelColor) + 128;
                int B = (Color.blue(pixelColor) + 128 > 255) ? 255 : Color.blue(pixelColor) + 128;
                if (labelData == 15) {
                	pixels[x + y * width] = Color.argb(
                			Color.alpha(255),
                			Color.red(R),
                			Color.green(G),
                			Color.blue(B)
                	);

                }
            }
        }
        outBitMap.setPixels(pixels, 0, width, 0, 0, width, height);

		// セグメンテーションした結果を表示
		ImageView imageView = (ImageView)findViewById(R.id.imageView1);
		imageView.setImageBitmap(outBitMap);
	}

	/**
	 * OkHttpを使ってbase64データを計算サーバーへpostする
	 * @param data
	 * @return
	 * @throws IOException
	 */
	private String postByOkHttp(String data) throws IOException {
		MediaType mediaType = MediaType.parse("text/plain");
		RequestBody body = RequestBody.create(mediaType, data);
		Request request = new Request.Builder()
				.url("http://192.168.100.87:8080/segmentation/json/")
				.post(body)
				.build();
		OkHttpClient client = new OkHttpClient();
		Response response;
		try {
			response = client.newCall(request).execute();
			return response.body().string();
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		return;

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.preview, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
