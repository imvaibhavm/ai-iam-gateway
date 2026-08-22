"use client";

import { Auth0Provider, useAuth0 } from "@auth0/auth0-react";
import { createContext, useCallback, useContext } from "react";

type IdentityAuth = {
  oidc: boolean;
  loading: boolean;
  authenticated: boolean;
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  login: () => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<IdentityAuth | null>(null);
const oidcEnabled = process.env.NEXT_PUBLIC_OIDC_ENABLED === "true";
const backend = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

function OidcIdentityProvider({ children }: { children: React.ReactNode }) {
  const { isLoading, isAuthenticated, getAccessTokenSilently, loginWithRedirect, logout } = useAuth0();
  const apiFetch = useCallback(async (path: string, init: RequestInit = {}) => {
    const token = await getAccessTokenSilently();
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${backend}/api${path}`, { ...init, headers });
  }, [getAccessTokenSilently]);
  return <AuthContext.Provider value={{
    oidc: true, loading: isLoading, authenticated: isAuthenticated, apiFetch,
    login: () => loginWithRedirect({ appState: { returnTo: "/" } }),
    logout: () => logout({ logoutParams: { returnTo: window.location.origin + "/login" } }),
  }}>{children}</AuthContext.Provider>;
}

function DevelopmentIdentityProvider({ children }: { children: React.ReactNode }) {
  const apiFetch = useCallback(async (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    const token = localStorage.getItem("aiguard_access_token");
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(`${backend}/api${path}`, { ...init, headers });
  }, []);
  return <AuthContext.Provider value={{
    oidc: false, loading: false, authenticated: true, apiFetch,
    login: async () => {}, logout: () => {},
  }}>{children}</AuthContext.Provider>;
}

export function IdentityAuthProvider({ children }: { children: React.ReactNode }) {
  if (!oidcEnabled) return <DevelopmentIdentityProvider>{children}</DevelopmentIdentityProvider>;
  const domain = process.env.NEXT_PUBLIC_AUTH0_DOMAIN;
  const clientId = process.env.NEXT_PUBLIC_AUTH0_CLIENT_ID;
  const audience = process.env.NEXT_PUBLIC_AUTH0_AUDIENCE;
  if (!domain || !clientId || !audience) throw new Error("OIDC public configuration is incomplete");
  return <Auth0Provider domain={domain} clientId={clientId} cacheLocation="memory" useRefreshTokens
    authorizationParams={{
      redirect_uri: process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000",
      audience,
      scope: "openid profile email offline_access",
    }}>
    <OidcIdentityProvider>{children}</OidcIdentityProvider>
  </Auth0Provider>;
}

export function useIdentityAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("IdentityAuthProvider is required");
  return context;
}
