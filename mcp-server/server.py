"""
MCP Governance Server — strict tool registry for the expense multi-agent system.

Authentication is token-based:
  - EMPLOYEE_TOKENS : comma-separated tokens for regular employees
  - MANAGER_TOKENS  : comma-separated tokens for managers

Managers have access to all tools including bulk-delete.
Employees can only delete by a specific date or a specific invoice.
"""

import os
import httpx
from fastmcp import FastMCP

AI_CORE_URL = os.getenv("AI_CORE_URL", "http://ai-core:8081")
PORT        = int(os.getenv("PORT", "8082"))
HOST        = os.getenv("HOST", "0.0.0.0")

# Chargement des tokens depuis les variables d'environnement
MANAGER_TOKENS  = {t.strip() for t in os.getenv("MANAGER_TOKENS",  "").split(",") if t.strip()}
EMPLOYEE_TOKENS = {t.strip() for t in os.getenv("EMPLOYEE_TOKENS", "").split(",") if t.strip()}

mcp = FastMCP(
    name="expense-governance-server",
)


# ─────────────────────────────────────────────
# Auth helpers
# ─────────────────────────────────────────────

def _role(token: str) -> str | None:
    """Retourne 'manager', 'employee' ou None si le token est inconnu."""
    if token in MANAGER_TOKENS:
        return "manager"
    if token in EMPLOYEE_TOKENS:
        return "employee"
    return None


def _require(token: str, min_role: str = "employee") -> str:
    """
    Valide le token et vérifie que le rôle est suffisant.
    Lève une PermissionError sinon.
    Retourne le rôle de l'utilisateur.
    """
    role = _role(token)
    if role is None:
        raise PermissionError("Token invalide — authentification refusée.")
    if min_role == "manager" and role != "manager":
        raise PermissionError("Accès refusé — seuls les managers peuvent effectuer cette opération.")
    return role


# ─────────────────────────────────────────────
# Tool 1 — Submit to full Kafka agent pipeline
# ─────────────────────────────────────────────
@mcp.tool()
def submit_to_pipeline(token: str, text: str, timeout_ms: int = 30000) -> dict:
    """
    Soumet une demande en langage naturel dans la chaîne complète des agents Kafka
    et attend le résultat final (intent-agent → reasoning-agent → reassign-agent).

    Utilise cet outil quand l'utilisateur veut :
      - créer une dépense  ("j'ai payé un taxi 42 EUR aujourd'hui")
      - générer un rapport ("montre-moi mes dépenses de mars")
      - générer une facture ("facture de conseil pour Acme Corp, 10 jours à 700€/j")

    Paramètres :
      - token      : token d'authentification de l'utilisateur (obligatoire)
      - text       : demande en langage naturel (obligatoire)
      - timeout_ms : délai maximum en millisecondes (défaut 30000 = 30 s)
    """
    _require(token)
    with httpx.Client(timeout=timeout_ms / 1000 + 10) as client:
        resp = client.post(
            f"{AI_CORE_URL}/pipeline/process",
            json={"text": text, "timeoutMs": timeout_ms},
        )
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Tool 2 — Get expense report
# ─────────────────────────────────────────────
@mcp.tool()
def get_expenses_report(
    token: str,
    start: str | None = None,
    end: str | None = None,
    expense_type: str | None = None,
    currency: str | None = None,
    company: str | None = None,
) -> dict:
    """
    Récupère un rapport de dépenses avec des filtres optionnels.

    Paramètres :
      - token        : token d'authentification (obligatoire)
      - start / end  : plage de dates au format YYYY-MM-DD
      - expense_type : filtre par catégorie ("transport", "repas", "hébergement")
      - currency     : filtre par devise ("EUR", "USD")
      - company      : filtre par société
    """
    _require(token)
    params: dict = {}
    if start:        params["start"]    = start
    if end:          params["end"]      = end
    if expense_type: params["type"]     = expense_type
    if currency:     params["currency"] = currency
    if company:      params["company"]  = company

    with httpx.Client(timeout=30) as client:
        resp = client.get(f"{AI_CORE_URL}/expenses/report", params=params)
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Tool 3 — Delete expenses by specific date (employee)
# ─────────────────────────────────────────────
@mcp.tool()
def delete_expense_by_date(token: str, date: str) -> dict:
    """
    Supprime définitivement toutes les dépenses enregistrées à une date précise.

    La date doit être au format YYYY-MM-DD (ex: "2024-03-15").
    Accessible à tous les utilisateurs authentifiés.

    Paramètres :
      - token : token d'authentification (obligatoire)
      - date  : date exacte des dépenses à supprimer (obligatoire)
    """
    _require(token)
    with httpx.Client(timeout=30) as client:
        resp = client.delete(
            f"{AI_CORE_URL}/expenses/delete",
            params={"date": date},
        )
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Tool 4 — Delete a specific invoice (employee)
# ─────────────────────────────────────────────
@mcp.tool()
def delete_invoice(
    token: str,
    invoice_name: str,
    seller_company_name: str,
    billing_month: str,
) -> dict:
    """
    Supprime définitivement une facture précise.
    Accessible à tous les utilisateurs authentifiés.

    Paramètres :
      - token               : token d'authentification (obligatoire)
      - invoice_name        : identifiant de la facture (ex: "FACT-2024-03-001")
      - seller_company_name : société émettrice
      - billing_month       : mois de facturation au format YYYY-MM
    """
    _require(token)
    with httpx.Client(timeout=30) as client:
        resp = client.post(
            f"{AI_CORE_URL}/invoices/delete",
            json={
                "invoiceName":       invoice_name,
                "sellerCompanyName": seller_company_name,
                "billingMonth":      billing_month,
            },
        )
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Tool 5 — Delete ALL expenses (manager only)
# ─────────────────────────────────────────────
@mcp.tool()
def delete_all_expenses(token: str, company: str | None = None) -> dict:
    """
    Supprime définitivement TOUTES les dépenses, sans filtre de date.
    Réservé aux managers uniquement.

    Paramètres :
      - token   : token manager (obligatoire)
      - company : restreindre la suppression à une société (optionnel)
    """
    _require(token, min_role="manager")
    params: dict = {}
    if company:
        params["company"] = company

    with httpx.Client(timeout=60) as client:
        resp = client.delete(
            f"{AI_CORE_URL}/expenses/delete-all",
            params=params,
        )
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Tool 6 — Delete ALL invoices (manager only)
# ─────────────────────────────────────────────
@mcp.tool()
def delete_all_invoices(token: str, seller_company_name: str | None = None) -> dict:
    """
    Supprime définitivement TOUTES les factures, sans filtre de mois.
    Réservé aux managers uniquement.

    Paramètres :
      - token               : token manager (obligatoire)
      - seller_company_name : restreindre à une société émettrice (optionnel)
    """
    _require(token, min_role="manager")
    params: dict = {}
    if seller_company_name:
        params["sellerCompanyName"] = seller_company_name

    with httpx.Client(timeout=60) as client:
        resp = client.delete(
            f"{AI_CORE_URL}/invoices/delete-all",
            params=params,
        )
        resp.raise_for_status()
        return resp.json()


# ─────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────
if __name__ == "__main__":
    mcp.run(transport="streamable-http", host=HOST, port=PORT)
