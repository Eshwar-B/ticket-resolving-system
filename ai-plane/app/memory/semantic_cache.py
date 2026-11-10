"""
Semantic / Query Cache with Strict Tenant Scoping.
Caches resolution summaries and RAG query results in Redis or local memory.
Redis key format: t:{tenant_id}:cache:{query_hash}
Zero cross-tenant cache contamination guaranteed.
"""
import hashlib
import json
import logging
from typing import Dict, Any, Optional
import os

logger = logging.getLogger(__name__)

class SemanticCache:
    """Tenant-scoped semantic cache with Redis backing and in-memory fallback."""

    def __init__(self, default_ttl_seconds: int = 3600):
        self.default_ttl = default_ttl_seconds
        self._memory_cache: Dict[str, Any] = {}
        self._redis_client = None
        self._init_redis()

    def _init_redis(self) -> None:
        try:
            import redis
            redis_host = os.getenv("REDIS_HOST", "localhost")
            redis_port = int(os.getenv("REDIS_PORT", "6379"))
            redis_password = os.getenv("REDIS_PASSWORD", "redis_dev_password")
            self._redis_client = redis.Redis(
                host=redis_host,
                port=redis_port,
                password=redis_password,
                decode_responses=True,
                socket_timeout=1.0,
            )
            self._redis_client.ping()
        except Exception as e:
            logger.info("Redis not directly connected in semantic cache, using local memory: %s", e)
            self._redis_client = None

    def _build_key(self, tenant_id: str, query: str) -> str:
        q_hash = hashlib.sha256(query.strip().lower().encode("utf-8")).hexdigest()
        return f"t:{tenant_id}:cache:{q_hash}"

    def get(self, tenant_id: str, query: str) -> Optional[Dict[str, Any]]:
        key = self._build_key(tenant_id, query)
        # Try Redis first
        if self._redis_client:
            try:
                val = self._redis_client.get(key)
                if val:
                    return json.loads(val)
            except Exception:
                pass
        # Fallback to local memory cache
        return self._memory_cache.get(key)

    def set(
        self,
        tenant_id: str,
        query: str,
        value: Dict[str, Any],
        ttl_seconds: Optional[int] = None,
    ) -> None:
        key = self._build_key(tenant_id, query)
        ttl = ttl_seconds or self.default_ttl
        # Store in local memory cache
        self._memory_cache[key] = value

        # Store in Redis if connected
        if self._redis_client:
            try:
                self._redis_client.setex(key, ttl, json.dumps(value))
            except Exception:
                pass

    def clear_tenant(self, tenant_id: str) -> int:
        """Deletes all cache keys for a specific tenant (compliance / forget-me)."""
        count = 0
        prefix = f"t:{tenant_id}:cache:"
        keys_to_del = [k for k in self._memory_cache if k.startswith(prefix)]
        for k in keys_to_del:
            del self._memory_cache[k]
            count += 1

        if self._redis_client:
            try:
                redis_keys = self._redis_client.keys(f"{prefix}*")
                if redis_keys:
                    self._redis_client.delete(*redis_keys)
                    count += len(redis_keys)
            except Exception:
                pass

        return count

_cache_instance: Optional[SemanticCache] = None

def get_semantic_cache() -> SemanticCache:
    global _cache_instance
    if _cache_instance is None:
        _cache_instance = SemanticCache()
    return _cache_instance
