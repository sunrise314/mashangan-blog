# -*- coding: utf-8 -*-
"""Import articles from quanxiaoha.com to Halo via RSC payload."""
import re
import base64
import time
import requests
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_v1_5

HALO_BASE = "http://49.235.136.65:8090"
SOURCE_BASE = "https://www.quanxiaoha.com"

session = requests.Session()
browser_headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}


def halo_login(username, password):
    resp = session.get(f"{HALO_BASE}/login")
    csrf = re.search(r'name="_csrf" value="([^"]+)"', resp.text).group(1)
    pub_b64 = re.search(r'const publicKey = "([^"]+)"', resp.text).group(1)
    pub_pem = "-----BEGIN PUBLIC KEY-----\n" + "\n".join(
        pub_b64[i:i+64] for i in range(0, len(pub_b64), 64)
    ) + "\n-----END PUBLIC KEY-----"
    rsa_key = RSA.import_key(pub_pem)
    enc = base64.b64encode(PKCS1_v1_5.new(rsa_key).encrypt(password.encode())).decode()
    resp = session.post(f"{HALO_BASE}/login", data={
        "username": username, "password": enc, "_csrf": csrf,
    }, allow_redirects=False)
    return resp.status_code in (302, 303)


def get_category_slugs():
    """Get all category slugs from homepage."""
    resp = requests.get(SOURCE_BASE + "/", headers=browser_headers)
    slugs = set(re.findall(r'href="/([a-z0-9-]+)/[a-z0-9-]+\.html"', resp.text))
    # Filter to only tutorial columns (exclude special pages)
    exclude = {"page", "column", "tools", "zsxq", "dev-tools", "java-interview"}
    return sorted(s for s in slugs if s not in exclude)


def get_article_links(category_slug):
    """Get all article links in a category from its tutorial page."""
    url = f"{SOURCE_BASE}/{category_slug}/{category_slug}-tutorial.html"
    resp = requests.get(url, headers=browser_headers)
    if resp.status_code != 200:
        return []
    links = re.findall(r'href="(/' + re.escape(category_slug) + r'/[^"]+\.html)"', resp.text)
    # Remove the tutorial index page itself
    return sorted(set(l for l in links if not l.endswith(f"/{category_slug}-tutorial.html")))


def get_article_data(article_path):
    """Fetch article title, cover, and content HTML."""
    url = SOURCE_BASE + article_path
    # Get title and cover from regular page
    resp = requests.get(url, headers=browser_headers)
    title_match = re.search(r'<title>([^<]+)</title>', resp.text)
    title = title_match.group(1).split("|")[0].strip() if title_match else article_path.split("/")[-1].replace(".html", "")
    cover_match = re.search(r'<meta property="og:image" content="([^"]+)"', resp.text)
    cover = cover_match.group(1) if cover_match else ""
    desc_match = re.search(r'<meta name="description" content="([^"]+)"', resp.text)
    excerpt = desc_match.group(1) if desc_match else ""

    # Get content from RSC payload
    rsc_headers = {**browser_headers, "RSC": "1"}
    rsc_resp = requests.get(url + "?_rsc=1", headers=rsc_headers)
    rsc_resp.encoding = "utf-8"
    c = rsc_resp.text

    # Extract article HTML: from first <p> to last </p>
    first_p = c.find("<p>")
    last_p = c.rfind("</p>")
    content = ""
    if first_p > 0 and last_p > first_p:
        content = c[first_p:last_p + 4]

    return title, cover, excerpt, content


def get_halo_categories():
    resp = session.get(f"{HALO_BASE}/apis/content.halo.run/v1alpha1/categories?size=1000")
    return {c["spec"]["slug"]: c["metadata"]["name"] for c in resp.json()["items"]}


def create_halo_category(display_name, slug, cover="", description=""):
    body = {
        "apiVersion": "content.halo.run/v1alpha1",
        "kind": "Category",
        "metadata": {"name": slug},
        "spec": {
            "displayName": display_name, "slug": slug, "cover": cover,
            "description": description, "template": "", "hideFromList": False,
            "preventParentPostCascadeQuery": False, "priority": 0,
        }
    }
    resp = session.post(f"{HALO_BASE}/apis/content.halo.run/v1alpha1/categories", json=body)
    if resp.status_code in (200, 201):
        return resp.json()["metadata"]["name"]
    print(f"    Create category failed: {resp.status_code} {resp.text[:200]}")
    return None


def create_halo_post(title, slug, html_content, category_name, cover="", excerpt=""):
    import uuid
    post_name = f"post-{slug}-{uuid.uuid4().hex[:8]}"
    post_body = {
        "apiVersion": "content.halo.run/v1alpha1",
        "kind": "Post",
        "metadata": {"name": post_name},
        "spec": {
            "title": title, "slug": slug, "categories": [category_name], "tags": [],
            "cover": cover,
            "excerpt": {"raw": excerpt[:200] if excerpt else "", "autoGenerate": not bool(excerpt)},
            "publish": False, "visible": "PUBLIC", "deleted": False, "pinned": False,
            "allowComment": True, "priority": 0,
        },
    }
    body = {
        "post": post_body,
        "content": {
            "raw": html_content,
            "content": html_content,
            "rawType": "HTML",
        },
    }
    resp = session.post(f"{HALO_BASE}/apis/api.console.halo.run/v1alpha1/posts", json=body)
    if resp.status_code not in (200, 201):
        print(f"    Create failed: {resp.status_code} {resp.text[:200]}")
        return False

    # Publish the post
    pub_resp = session.put(f"{HALO_BASE}/apis/api.console.halo.run/v1alpha1/posts/{post_name}/publish")
    if pub_resp.status_code == 200:
        return True
    print(f"    Publish failed: {pub_resp.status_code} {pub_resp.text[:200]}")
    return False


def main():
    if not halo_login("admin", "123456"):
        print("Login failed"); return
    print("Login OK")

    halo_cats = get_halo_categories()
    print(f"Existing categories: {len(halo_cats)}")

    cat_slugs = get_category_slugs()
    print(f"Found {len(cat_slugs)} categories: {cat_slugs}")

    total_posts = 0
    for cat_slug in cat_slugs:
        print(f"\n=== Category: {cat_slug} ===")
        article_links = get_article_links(cat_slug)
        print(f"  Articles: {len(article_links)}")
        if not article_links:
            continue

        # Create category in Halo
        if cat_slug not in halo_cats:
            halo_cat_name = create_halo_category(cat_slug, cat_slug)
            if halo_cat_name:
                halo_cats[cat_slug] = halo_cat_name
                print(f"  Created category: {cat_slug}")
            else:
                print(f"  Failed to create category, skipping"); continue
        else:
            halo_cat_name = halo_cats[cat_slug]

        for link in article_links:
            article_slug = link.split("/")[-1].replace(".html", "")
            try:
                title, cover, excerpt, content = get_article_data(link)
                if not content:
                    print(f"  [{article_slug}] No content, skip"); continue
                ok = create_halo_post(title, article_slug, content, halo_cat_name, cover, excerpt)
                if ok:
                    total_posts += 1
                    print(f"  [{article_slug}] OK: {title}")
                else:
                    print(f"  [{article_slug}] FAIL")
            except Exception as e:
                print(f"  [{article_slug}] Error: {e}")
            time.sleep(0.2)

    print(f"\n=== Done! Total imported: {total_posts} ===")


if __name__ == "__main__":
    main()
