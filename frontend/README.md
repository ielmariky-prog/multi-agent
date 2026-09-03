# Frontend justificatifs

Mini interface web pour capturer une photo (caméra) ou déposer un fichier (PDF/JPG/PNG) et l’envoyer vers l’endpoint d’upload (`/receipts/upload`).

## Démarrage local
```bash
cd frontend
python -m http.server 3000  # ou npx serve .
```
Ouvre ensuite http://localhost:3000. Mets à jour l’URL de l’endpoint dans le champ “Endpoint upload” si ton API tourne ailleurs.

## Déploiement statique
- Option simple : servir le dossier `frontend` via un Nginx / bucket statique (S3/CloudFront, GCS, Netlify, Vercel).  
- Il suffit de publier `index.html`, `style.css`, `app.js` tels quels (aucun build requis).

## Intégration Kubernetes (exemple Nginx)
- Construire une image nginx contenant les fichiers :
  ```bash
  cat > frontend/Dockerfile <<'EOF'
  FROM nginx:alpine
  COPY . /usr/share/nginx/html
  EOF
  docker build -t my-frontend:latest frontend
  ```
- Déployer un Service/Deployment Nginx exposant le port 80, ou un Ingress si besoin.
- Configure dans la page l’URL de l’endpoint (`/receipts/upload`) pointant vers ton backend (ai-core).

## Notes
- Les captures caméra nécessitent HTTPS en prod (ou `localhost` en dev) pour l’accès `getUserMedia`.
- L’endpoint doit accepter un `multipart/form-data` avec le champ `file`.
