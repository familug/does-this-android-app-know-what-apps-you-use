# does-this-android-app-know-what-apps-you-use

Blog <https://peabee.substack.com/p/everyone-knows-what-apps-you-use> phát hiện ra nhiều Android app đã lách cơ chế của Play Store để có thể xem các app nào cài trên máy. Gồm 2 cơ chế trong file AndroidManifest.xml:

- liệt kê cụ thể từng app ra qua package id
- khai báo `queries android.intent.action.MAIN` để có thể list tất cả app đã cài

```
<queries>
  [...]
  <intent>
    <action android:name="android.intent.action.MAIN" />
  </intent>
  [...]
</queries>
```

Repo này cung cấp công cụ để tìm hiểu các app nào có các quyền nói trên.

## Mục tiêu
Liệt kê top 100 app phổ biến nhất ở Google Play store Việt Nam.

## Kết quả
Xem kết quả tại [apps.csv](./apps.csv)

## Đóng góp

Vào https://play.google.com tìm app cần kiểm tra, ví dụ facebook

Sửa file `pkgs.txt` thêm URL package cần kiểm tra vào 1 dòng mới, tạo Pull R.

## Cài đặt
Trên Ubuntu, tải repo về, chạy cài đặt

Tested on Ubuntu 22.04
Requirements:

```
sudo apt install -y appt unzip
pip install --upgrade uv; uvx playwright install firefox --with-deps
```

## Chạy


### Container
```
podman build . -t androidleak
podman run -v $PWD:/app androidleak 'https://play.google.com/store/apps/details?id=com.netflix.mediaclient'
```

### Local
```
uv run main.py  --url 'https://play.google.com/store/apps/details?id=com.facebook.katana'
```

## TODO
- tải app trực tiếp từ Google Play Store
- tạo website check trực tiếp theo yêu cầu
- Get list of top app on Google Play Store , e.g in Vietnam
- Try extract manifest file via jadx
