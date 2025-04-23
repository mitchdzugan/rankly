import requests
from bs4 import BeautifulSoup
import json

def flat_map(f, xs):
    return list(y for ys in xs for y in f(ys))

def group_by(f, xs):
    res = {}
    for x in xs:
        k = f(x)
        if k in res:
            res[k].append(x)
        else:
            res[k] = [x]
    return res

def get_document(url):
    x = requests.get(url)
    return x.text

def get_episode_num(num_text):
    try:
        return int(num_text.replace("#", ""))
    except:
        return 0

def get_episode_image(td_soup):
    ep_href = td_soup.select('a')[0]["href"]
    url = f"https://lostpedia.fandom.com{ep_href}"
    soup = BeautifulSoup(get_document(url), features="html.parser")
    return soup.select('aside img.pi-image-thumbnail')[0]["src"]

TITLE_SUBSTITUIONS = {
    "There's No Place Like Home, Part 2": "There's No Place Like Home, Parts 2 & 3",
    "The Incident, Part 1": "The Incident",
    "LA X, Part 1": "LA X",
    "Live Together, Die Alone, Part 1": "Live Together, Die Alone",
}

def get_ep_data(soup):
    try:
        tds = soup.select('td')
        num = get_episode_num(tds[0].text)
        title = tds[1].text
        title = title[1: len(title) - 1]
        if title in TITLE_SUBSTITUIONS:
            title = TITLE_SUBSTITUIONS[title]
        print(title)
        image = get_episode_image(tds[1])
        return { "num": num, "title": title, "image": image }
    except:
        return { "num": 0, "title": "", "image": "" }

def get_eps_data_from_column(column_soup):
    return list(map(get_ep_data, column_soup.select('tr')))

def get_season_eps(num):
    url = f"https://lostpedia.fandom.com/wiki/Season_{num}"
    soup = BeautifulSoup(get_document(url), features="html.parser")
    eps_columns = soup.select('table.navbox table.navbox-columns-table table')
    eps = flat_map(get_eps_data_from_column, eps_columns)
    eps = list(filter(lambda ep: ep["num"] > 0, eps))
    res = []
    for group in group_by(lambda ep: ep["image"], eps).values():
        ep = group[0]
        ep_num = ep["num"]
        ep_title = ep["title"]
        ep_image = ep["image"]
        label = f"<strong>{num}.{ep_num})</strong> {ep_title}"
        res.append({ "label": label, "image": ep_image })
    return res

eps = flat_map(get_season_eps, [1, 2, 3, 4, 5, 6])
with open('./LOST_EPS.json', 'w') as f:
    f.write(json.dumps({ "name": "LOST Episodes", "elements": eps }) + "\n")
