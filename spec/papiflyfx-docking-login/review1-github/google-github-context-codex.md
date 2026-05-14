Short answer: **you can’t directly use Google as an identity provider to modify GitHub resources**.
Google and GitHub are separate identity domains.

But there *is* a correct architecture to achieve what you want 👇

---

# 🚫 Why Google → GitHub Directly Doesn’t Work

* Google (OIDC provider) → proves **who the user is**
* GitHub (OAuth provider) → controls **GitHub resources**

👉 GitHub does **not accept Google-issued tokens** for API access.

So this won’t work:

```
Google login → use Google token → call GitHub API ❌
```

---

# ✅ Correct Approach (Industry Standard)

You need **two separate flows**:

### 1. Authenticate user with Google (OIDC)

### 2. Authorize access to GitHub (OAuth)

---

# 🔄 High-Level Flow

```
[User]
   ↓
(1) Login with Google (OIDC)
   ↓
[Your App knows WHO the user is]
   ↓
(2) Connect GitHub account (OAuth)
   ↓
[Your App gets GitHub access token]
   ↓
(3) Call GitHub API (modify repos, commits, PRs)
```

---

# 🧩 Step-by-Step Implementation

## 🪪 Step 1 — Authenticate with Google (OIDC)

Use Google as your login provider:

* Request scope: `openid email profile`
* Receive:

    * **ID Token (JWT)** → contains user identity
    * (Optional) Access token for Google APIs

👉 Result:

```json
{
  "sub": "google-user-id",
  "email": "user@gmail.com",
  "name": "User Name"
}
```

Store:

* `google_sub`
* email

---

## 🔗 Step 2 — Link GitHub Account (OAuth)

Now prompt user:

> “Connect your GitHub account”

Use GitHub OAuth:

* Endpoint: `https://github.com/login/oauth/authorize`
* Scopes (important):

    * `repo` → full repo access
    * `workflow` → GitHub Actions
    * `read:user` → profile

👉 User approves → you get:

```json
{
  "access_token": "gho_xxx..."
}
```

---

## 🔐 Step 3 — Store GitHub Token Securely

* Encrypt at rest
* Associate with your internal user:

```
user_id (Google) ↔ github_access_token
```

---

## ⚙️ Step 4 — Call GitHub API

Use the GitHub token:

Example: Create a commit

```bash
POST https://api.github.com/repos/{owner}/{repo}/contents/{path}
Authorization: Bearer <github_access_token>
```

---

## 🧠 Important Design Insight

You are building a **federated identity + multi-provider system**:

* Google = **Authentication provider**
* GitHub = **Resource provider**

👉 Your app becomes the **identity broker**

---

# 🏗️ Architecture for Your Docking Framework

Since you're building a modular login system:

### Suggested Interfaces

```java
interface IdentityProvider {
    UserIdentity authenticate();
}

interface ResourceProvider {
    AccessToken authorize(UserIdentity user);
}
```

### Concrete Implementations

```java
class GoogleOIDCProvider implements IdentityProvider {}
class GitHubOAuthProvider implements ResourceProvider {}
```

---

# 🔒 Security Best Practices

* Never trust Google token for GitHub
* Validate:

    * Google ID token (signature, audience, issuer)
    * GitHub token scopes
* Use **PKCE flow** for OAuth
* Refresh tokens if supported

---

# ⚠️ Alternative (Advanced)

If you want tighter integration:

### Use GitHub as Identity Provider instead

GitHub supports OAuth login:

```
Login with GitHub → same token → API access
```

👉 Simpler, but:

* No Google login
* Less flexible identity system

---

# ✅ Final Takeaway

* Google → **who the user is**
* GitHub → **what the user can do on GitHub**

👉 You must use **both flows separately**, then link them in your system.

---

If you want, I can design a **full sequence diagram + JavaFX module structure** for your `papiflyfx-docking-login` and `papiflyfx-docking-github` integration.
