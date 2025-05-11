const INITIAL_RATING = 1000
const K_FACTOR = 30

const updateRating = (elements, mu) => {
    const winProbability = (r1, r2) => (
        1.0 / (1.0 + Math.pow(10, (r1 - r2) / 400.0))
    );
    const ew = elements[mu.w];
    const el = elements[mu.l];
    const pw = winProbability(ew.rating, el.rating);
    const pl = winProbability(el.rating, ew.rating);
    ew.rating += K_FACTOR * (1 - pw);
    el.rating += K_FACTOR * (0 - pl);
};

const getLocalStorage = () => {
    try {
        return JSON.parse(localStorage.getItem('db'));
    }
    catch (e) {
        return {};
    }
};

const reverse = (arr) => {
    const res = [...arr];
    res.reverse();
    return res;
};

const sortBy = (arr, f) => {
    const res =[...arr];
    res.sort((v1, v2) => f(v1) - f(v2));
    return res;
};

const groupBy = (arr, f) => {
    const res = {};
    for (const el of arr) {
        const k = f(el);
        res[k] = res[k] || [];
        res[k].push(el);
    }
    return res;
};

const bind = (arr, f) => {
    const res = [];
    for (const el of arr) {
        for (const resEl of f(el)) {
            res.push(resEl);
        }
    }
    return res;
};

const minBy = (arr, f) => {
    let minEl = arr[0];
    let minVal = f(minEl);
    for (const el of arr) {
        const v = f(el);
        if (v < minVal) {
            minVal = v;
            minEl = el;
        }
    }
    return minEl;
};

const randEl = (arr) => {
    const ind = Math.floor(Math.random() * arr.length);
    return arr[ind];
};

function El(props) {
    const [isLoaded, setIsLoaded] = React.useState(false);
    React.useEffect(() => { setTimeout(() => setIsLoaded(true), 1000) }, []);
    if (!isLoaded) {
        return (
            <div
                className="card"
            >
                <div className="card-content">
                    <p 
                        dangerouslySetInnerHTML={{ __html: "loading..." }}
                    />
                </div>
                <div className="card-image">
                    <img src={props.el.image} />
                </div>
            </div>

        );
    }
    return (
        <div
            className="card"
            style={{ cursor: 'pointer' }}
            onClick={props.onClick}
        >
            <div className="card-content">
                <p 
                    dangerouslySetInnerHTML={{ __html: props.el.label }}
                />
            </div>
            <div className="card-image">
                <img src={props.el.image} />
            </div>
        </div>
    );
}

function App() {
    const [rawDb, rawSetDB] = React.useState(getLocalStorage());
    const db = rawDb || {};
    const [active, setActive] = React.useState(null);
    const setDB = (db) => {
        localStorage.setItem('db', JSON.stringify(db));
        rawSetDB(db);
    };
    const handleFilePick = (e) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            const source = JSON.parse(e.target.result);
            const sources = { ...(db.sources || {}), [source.name]: source };
            setDB({ ...db, sources });
        };
        reader.readAsText(e.target.files[0]);
    };

    if (!active || !db.sources[active]) {
        return (
            <div>
                <input onChange={handleFilePick} type="file" />
                <label htmlFor="select-active">Select Rankly</label>
                <select
                    onChange={(e) => setActive(e.target.value)}
                    id="select-active"
                >
                    <option key="" value="">--Please choose an option--</option>
                    {Object.keys(db.sources || {}).map((name) => (
                        <option key={name} value={name}>{name}</option>
                    ))}
                </select>
            </div>
        );
    }

    const rankly = db.sources[active];
    const mus = rankly.mus || [];
    const elements = rankly.elements.map((el, id) => ({ 
        ...el, id, mus: new Set([]), rating: INITIAL_RATING
    }));
    for (const mu of mus) {
        elements[mu.w].mus.add(mu.l);
        elements[mu.l].mus.add(mu.w);
    }
    const byMuCount = groupBy(elements, (el) => el.mus.size);
    const minMuCountEl = minBy(elements, (el) => el.mus.size);
    const minMuCount = minMuCountEl.mus.size;
    const numElements = elements.length;

    let muCount = minMuCount;
    let mu1Choices = [];
    while (muCount < numElements - 1 && mu1Choices.length < numElements / 2) {
        mu1Choices = [
            ...mu1Choices,
            ...(byMuCount[muCount] || []).map((el) => el.id)
        ];
        muCount++;
    }

    const mu1 = randEl(mu1Choices);

    muCount = minMuCount;
    let mu2Choices = [];
    while (muCount < numElements - 1 && mu2Choices.length < numElements / 2) {
        mu2Choices = [
            ...mu2Choices,
            ...((byMuCount[muCount] || [])
                .filter((el) => el.id !== mu1 && !el.mus.has(mu1))
                .map((el) => el.id))
        ];
        muCount++;
    }
    const mu2 = randEl(mu2Choices);
    
    for (const mu of mus) { updateRating(elements, mu); }
    for (const mu of reverse(mus)) { updateRating(elements, mu); }

    const x = bind(mus, ({ w, l }) => ([
        elements.map(({ id }) => (id === w ? 1 : (id === l ? -1 : 0))),
        elements.map(() => 1),
    ]))
    const y = bind(mus, () => ([[2], [0]]));
    const mlr = new window.MLR(x, y, { intercept: false });
    console.log({ mlr });
    mlr.weights.forEach((weight, id) => { elements[id].mlrRating = weight; });

    const sorted = sortBy(elements, (el) => -1 * el.mlrRating);

    const reportMu = (w, l) => {
        setDB({
            ...db,
            sources: {
                ...db.sources,
                [active]: {
                    ...rankly,
                    mus: [
                        ...(rankly.mus || []),
                        { w, l }
                    ],
                },
            },
        })
    };

    const deleteActive = () => {
        const nextSources = { ...db.sources };
        delete nextSources[active];
        setDB({ ...db, sources: nextSources });
    };

    /*
    const lpad = (n) => {
        if (n < 10) { return `00${n}`; }
        if (n < 100) { return `0${n}`; }
        return n;
    };
    console.log(groupBy(mus, ({ w, l }) => `${lpad(Math.min(w, l))}.${lpad(Math.max(w, l))}`));
    */

    // <input onChange={handleFilePick} type="file" />
    return (
        <div style={{ display: 'flex', flexDirection: 'column' }} >
            <div
                key={mus.length}
                style={{ display: 'flex', flexDirection: 'row' }} 
            >
                <input onChange={handleFilePick} type="file" />
                <button className="button" onClick={deleteActive} >
                    delete active
                </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'row' }} >
                <div style={{ flex: 1 }} />
                <El key={`${mus.length}.${mu1}`} el={elements[mu1]} onClick={() => reportMu(mu1, mu2)} />
                <div style={{ flex: 1 }} />
                <El key={`${mus.length}.${mu2}`} el={elements[mu2]} onClick={() => reportMu(mu2, mu1)} />
                <div style={{ flex: 1 }} />
            </div>
            {mus.length} / {(numElements * (numElements - 1)) / 2} ({numElements})
            <button className="button" onClick={() => setDB({ ...db })} >new mu</button>
            {sorted.map((el) => (
                <div
                    style={{ display: 'flex', flexDirection: 'row' }} 
                >
                    <div style={{ flex: 1 }} />
                    <p dangerouslySetInnerHTML={{ __html: el.label }} />
                    <div style={{ flex: 1 }} />
                    <p>{el.rating}</p>
                    <div style={{ flex: 1 }} />
                    <p>{el.mlrRating}</p>
                    <div style={{ flex: 1 }} />
                </div>
            ))}
        </div>
    );
}

function main() {
    const container = document.getElementById('app');
    const root = ReactDOM.createRoot(container);
    root.render(<App />);
}

(() => {
    main();
})();
