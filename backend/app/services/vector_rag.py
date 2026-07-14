"""
Vector-based RAG — semantic retrieval over the legal knowledge base using
ChromaDB as the vector store.

Embedding choice: ChromaDB's *default* embedding function downloads a
~90MB ONNX model from the internet on first use. In a sandboxed / offline
/ flaky-network deployment environment that download can fail, which would
silently break retrieval. To keep this reliable without any external
dependency, we supply our own embedding function backed by scikit-learn's
TF-IDF vectorizer (fit once over the fixed legal knowledge base at
startup). This is a real vector space with real cosine-similarity
retrieval — not a neural embedding, but genuinely vector search, not
keyword substring matching. If you later want neural embeddings (e.g.
sentence-transformers or an API-based embedding model), swap out
_TfidfEmbeddingFunction below; the rest of the retrieval pipeline is
unchanged.

Falls back to the original keyword search only if Chroma itself is
unavailable for some reason — retrieval never silently returns nothing.
"""

import os
os.environ.setdefault("ANONYMIZED_TELEMETRY", "False")

from typing import List, Optional

import chromadb
from chromadb import Documents, EmbeddingFunction, Embeddings
from sklearn.feature_extraction.text import TfidfVectorizer

from app.knowledge_base.legal_kb import LEGAL_KNOWLEDGE_BASE, LegalDoc, search_kb as keyword_search_kb

_BACKEND_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_CHROMA_DIR = os.path.join(_BACKEND_ROOT, "chroma_store")

_doc_by_id = {doc.id: doc for doc in LEGAL_KNOWLEDGE_BASE}
_VALID_CATEGORIES = {doc.category for doc in LEGAL_KNOWLEDGE_BASE}


def _doc_text(doc: LegalDoc) -> str:
    return f"{doc.title}. {doc.summary} Applicable when: {'; '.join(doc.applicable_when)}. Keywords: {', '.join(doc.keywords)}"


class _TfidfEmbeddingFunction(EmbeddingFunction):
    """
    Fits a TF-IDF vectorizer once over the fixed legal knowledge base corpus,
    then embeds any query/document into that same vector space. Fully
    offline and deterministic — no model download, no network call.
    """
    def __init__(self):
        corpus = [_doc_text(doc) for doc in LEGAL_KNOWLEDGE_BASE]
        self.vectorizer = TfidfVectorizer(stop_words="english", max_features=2000)
        self.vectorizer.fit(corpus)

    def __call__(self, input: Documents) -> Embeddings:
        matrix = self.vectorizer.transform(input)
        return matrix.toarray().tolist()


_embedding_fn = _TfidfEmbeddingFunction()
_client = None
_collection = None


def _get_collection():
    global _client, _collection
    if _collection is not None:
        return _collection
    try:
        _client = chromadb.PersistentClient(path=_CHROMA_DIR)
        _collection = _client.get_or_create_collection(
            name="cyber_legal_kb_tfidf",
            embedding_function=_embedding_fn,
        )
        if _collection.count() == 0:
            _collection.add(
                ids=[doc.id for doc in LEGAL_KNOWLEDGE_BASE],
                documents=[_doc_text(doc) for doc in LEGAL_KNOWLEDGE_BASE],
                metadatas=[{"category": doc.category} for doc in LEGAL_KNOWLEDGE_BASE],
            )
        return _collection
    except Exception:
        return None


def vector_search_kb(query: str, category: Optional[str] = None, top_k: int = 3) -> List[LegalDoc]:
    collection = _get_collection()
    if collection is None:
        return keyword_search_kb(query, category=category, top_k=top_k)

    try:
        # Only filter by category if it's a real KB category — the intent
        # classifier also produces pseudo-categories (e.g. "other_cyber")
        # that never appear in the KB, and filtering on those would always
        # return zero results.
        where = {"category": category} if (category and category in _VALID_CATEGORIES) else None
        results = collection.query(query_texts=[query], n_results=top_k, where=where)
        ids = results.get("ids", [[]])[0]
        docs = [_doc_by_id[i] for i in ids if i in _doc_by_id]
        return docs
    except Exception:
        return keyword_search_kb(query, category=category, top_k=top_k)
