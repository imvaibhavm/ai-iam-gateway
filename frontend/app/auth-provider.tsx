"use client";

import { Auth0Provider, useAuth0 } from "@auth0/auth0-react";
import { createContext, useCallback, useContext, useMemo } from "react";

type IdentityAuth = {
  oidc: boolean;
  loading: boolean;
  authenticated: boolean;
  userEmail?: string;
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  login: () => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<IdentityAuth | null>(null);
const oidcEnabled = process.env.NEXT_PUBLIC_OIDC_ENABLED === "true";
const backend = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
const auth0Audience = process.env.NEXT_PUBLIC_AUTH0_AUDIENCE;
const oidcScopes = "openid profile email";

function OidcIdentityProvider({ children }: { children: React.ReactNode }) {
  const { isLoading, isAuthenticated, user, getAccessTokenSilently, loginWithRedirect, logout } = useAuth0();
  const apiFetch = useCallback(async (path: string, init: RequestInit = {}) => {
    if (!auth0Audience) throw new Error("OIDC audience is not configured");
    const token = await getAccessTokenSilently({
      authorizationParams: { audience: auth0Audience, scope: oidcScopes },
    });
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${backend}/api${path}`, { ...init, headers });
  }, [getAccessTokenSilently]);
  const value = useMemo<IdentityAuth>(() => ({
    oidc: true, loading: isLoading, authenticated: isAuthenticated, userEmail: user?.email, apiFetch,
    login: () => loginWithRedirect({ appState: { returnTo: "/" } }),
    logout: () => logout({ logoutParams: { returnTo: window.location.origin + "/login" } }),
  }), [apiFetch, isAuthenticated, isLoading, loginWithRedirect, logout, user?.email]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function DevelopmentIdentityProvider({ children }: { children: React.ReactNode }) {
  const apiFetch = useCallback(async (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    const token = localStorage.getItem("aiguard_access_token");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${backend}/api${path}`, { ...init, headers });
  }, []);
  const value = useMemo<IdentityAuth>(() => ({
    oidc: false, loading: false, authenticated: true, apiFetch,
    login: async () => {}, logout: () => {},
  }), [apiFetch]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function IdentityAuthProvider({ children }: { children: React.ReactNode }) {
  if (!oidcEnabled) return <DevelopmentIdentityProvider>{children}</DevelopmentIdentityProvider>;
  const domain = process.env.NEXT_PUBLIC_AUTH0_DOMAIN;
  const clientId = process.env.NEXT_PUBLIC_AUTH0_CLIENT_ID;
  if (!domain || !clientId || !auth0Audience) throw new Error("OIDC public configuration is incomplete");
  return <Auth0Provider domain={domain} clientId={clientId} cacheLocation="memory"
    authorizationParams={{
      redirect_uri: process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000",
      audience: auth0Audience,
      scope: oidcScopes,
    }}>
    <OidcIdentityProvider>{children}</OidcIdentityProvider>
  </Auth0Provider>;
}

export function useIdentityAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("IdentityAuthProvider is required");
  return context;
}
