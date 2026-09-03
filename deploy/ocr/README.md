# OCR local (Tesseract + poppler)

## Installation rapide

### Debian/Ubuntu
```bash
sudo apt-get update
sudo apt-get install -y tesseract-ocr tesseract-ocr-fra poppler-utils
```

### macOS (Homebrew)
```bash
brew install tesseract poppler
# langues supplémentaires si besoin
brew install tesseract-lang
```

### Vérification
```bash
tesseract --version
pdftoppm -v
```

## Notes K8s / Docker
- Ajoute ces paquets dans l’image `ai-core` (Dockerfile) ou dans l’init image.
- Variables utilisées par `ai-core` :
  - `AI_CORE_OCR_TESSERACT_CMD` (par défaut `tesseract`)
  - `AI_CORE_OCR_PDFTOPPM_CMD` (par défaut `pdftoppm`)
  - `AI_CORE_OCR_LANG` (ex: `eng+fra`)
  - `AI_CORE_OCR_DPI` (ex: `300`)
  - `AI_CORE_RECEIPTS_STORAGE_PATH` (chemin de stockage des fichiers)
