Doze 모드 (Android 6+)
화면 꺼짐·충전 안 함·기기 미사용 시, 네트워크 접근이 주기적으로만 허용됩니다. 백그라운드 서비스의 네트워크 호출이 지연됨.
앱 대기(App Standby)
장기간 사용하지 않은 앱은 네트워크 접근이 제한되고, FCM high-priority 외 요청이 늦게 처리.
배터리 최적화
백그라운드에서 네트워크 호출이 중단·지연될 수 있음. IGNORE_BATTERY_OPTIMIZATION_SETTINGS로 예외 요청 가능.
제조사 커스텀 절전 정책 (삼성, 샤오미 등)
OS 표준보다 더 강하게 백그라운드 네트워크를 차단할 수 있음. “절전 앱” 목록에 들어가면 FCM조차 지연.
백그라운드 제한 설정 (사용자 설정)
앱이 아예 백그라운드에서 실행되지 않음 → 네트워크도 당연히 끊김.

좋아요—Vite 사용 시 APK가 .apk로 정확히 내려가고, 내부망 폰에서도 접속되도록 딱 필요한 설정만 정리했습니다.
핵심은 public/에 APK 두기 + Vite에서 .apk 요청에 올바른 헤더를 주는 것입니다.

⸻

1) 프로젝트에 APK 두기

your-vite-project/
  public/
    downloads/
      app-release-1.2.3.apk

빌드 후 경로: /downloads/app-release-1.2.3.apk

⸻

2) vite.config.ts (dev/preview 공통 헤더 적용)

Vite dev 서버(개발)와 vite preview(빌드 검증)에서 모두 .apk에 MIME/다운로드 헤더를 셋팅합니다.
또한 같은 네트워크의 휴대폰 접속을 위해 host: true 설정을 켜줍니다.

// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

function apkHeaderMiddleware() {
  return {
    name: 'apk-headers',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url?.endsWith('.apk')) {
          res.setHeader('Content-Type', 'application/vnd.android.package-archive')
          res.setHeader('Content-Disposition', `attachment; filename="${path.basename(req.url)}"`)
          res.setHeader('X-Content-Type-Options', 'nosniff')
        }
        next()
      })
    }
  }
}

export default defineConfig({
  plugins: [vue(), apkHeaderMiddleware()],
  server: {
    host: true,        // 0.0.0.0 바인드 → 같은 Wi-Fi 폰에서 접속 가능
    port: 5173,
    headers: {         // 전역 보강(선택)
      'X-Content-Type-Options': 'nosniff'
    }
  },
  preview: {
    host: true,
    port: 5174,
    headers: {
      'X-Content-Type-Options': 'nosniff'
    }
  }
})

접속 예:

개발:   http://<맥IP>:5173/downloads/app-release-1.2.3.apk
프리뷰: http://<맥IP>:5174/downloads/app-release-1.2.3.apk

주의: HTTP 환경에서는 크롬의 “안전하지 않은 다운로드” 경고는 뜹니다(정상).
경고까지 없애려면 내부망이라도 HTTPS(+사설 CA 설치)가 필요합니다.

⸻

3) 프로덕션 배포(정적 서버) 옵션

(A) vite build + Nginx

Vite로 빌드 후 Nginx에서 정적 서빙하며 .apk에 헤더를 보강합니다.

server {
  listen 8080;
  server_name _;

  root /var/www/myapp/dist;
  index index.html;

  # APK MIME 매핑
  types { application/vnd.android.package-archive apk; }

  location / {
    try_files $uri $uri/ /index.html;
  }

  # APK 헤더 보강
  location ~* \.apk$ {
    add_header Content-Disposition 'attachment; filename="$request_filename"';
    add_header X-Content-Type-Options nosniff;
  }
}

(B) vite preview로 간단 검증

npm run build
npm run preview    # 5174로 서비스

위 vite.config.ts의 preview.headers가 적용됩니다.

⸻

4) 간단한 다운로드 페이지(선택)

public/index.html에 링크만 추가해도 배포가 편해집니다.

<ul>
  <li><a href="/downloads/app-release-1.2.3.apk" download>APK 다운로드 v1.2.3</a></li>
</ul>
<p>설치 안내: 설정 &gt; 보안 &gt; 알 수 없는 앱 설치 &gt; (사용 브라우저) 허용</p>


⸻

5) 폰에서 접속 안 될 때 체크
	•	Vite dev/preview에 host: true 설정했는지
	•	맥 방화벽에서 5173/5174 허용했는지 (시스템 설정 > 보안 및 개인 정보 보호 > 방화벽)
	•	같은 서브넷(예: 192.168.0.x)인지
	•	파일명에 공백/한글 없는지 (app-release-1.2.3.apk 권장)
	•	브라우저 캐시 문제 시 강력 새로고침

⸻

필요하시면 Nginx로 운영 배포 또는 내부망 HTTPS(사설 CA) 템플릿도 바로 드릴게요.