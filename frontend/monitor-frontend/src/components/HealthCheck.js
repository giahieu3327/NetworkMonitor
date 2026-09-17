import React, { useEffect, useState } from "react";

function HealthCheck() {
  const [dbStatus, setDbStatus] = useState("");
  const [redisStatus, setRedisStatus] = useState("");
  const [keycloakStatus, setKeycloakStatus] = useState("");

  useEffect(() => {
    const API_BASE = "http://192.168.1.241:5000";

    fetch(`${API_BASE}/health/db`)
      .then(res => res.text())
      .then(setDbStatus)
      .catch(err => setDbStatus("Error connecting"));

    fetch(`${API_BASE}/health/redis`)
      .then(res => res.text())
      .then(setRedisStatus)
      .catch(err => setRedisStatus("Error connecting"));

    fetch(`${API_BASE}/health/keycloak`)
      .then(res => res.text())
      .then(setKeycloakStatus)
      .catch(err => setKeycloakStatus("Error connecting"));
  }, []);

  return (
    <div>
      <h2>Health Check</h2>
      <p>DB: {dbStatus}</p>
      <p>Redis: {redisStatus}</p>
      <p>Keycloak: {keycloakStatus}</p>
    </div>
  );
}

export default HealthCheck;