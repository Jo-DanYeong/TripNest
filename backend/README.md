# TripNest Backend

Production-ready backend for TripNest Android.

## Features

- Trip recommendation API with ad-like source filtering
- AI summary generation via Groq (`GROQ_API_KEY`)
- Nearby places API around a user-selected map center
  - stays (`AD5`)
  - attractions (`AT4`)
  - restaurants (`FD6`)

## Run locally

```powershell
cd backend
Copy-Item .env.example .env
npm run dev
```

Default URL: `http://localhost:8080`

## Required env vars

- `PORT` (default `8080`)
- `GROQ_API_KEY`
- `GROQ_MODEL` (default `llama-3.3-70b-versatile`)
- `KAKAO_REST_API_KEY` (required for real nearby/category place search)

## API endpoints

1. `GET /api/health`
2. `POST /api/trips/recommendations`
3. `POST /api/maps/nearby`

Nearby request body example:

```json
{
  "latitude": 37.5665,
  "longitude": 126.9780,
  "radiusMeters": 2000
}
```

## Deployment

This backend is ready for container hosting.

1. Build image:
`docker build -t tripnest-backend ./backend`
2. Run:
`docker run -p 8080:8080 --env-file ./backend/.env tripnest-backend`
3. Deploy to Render/Railway/Fly with `backend/Dockerfile`
4. Bind custom domain (for example `api.tripnest.kr`)
5. Set Android `BACKEND_BASE_URL` to that HTTPS domain

## Kakao keys

- Android app map SDK: **Native App Key**
- Backend local/category search API: **REST API Key**
- Web map app: **JavaScript Key**

For this Android project, we use:
- Native key in the app (manifest placeholder)
- REST key in backend env
