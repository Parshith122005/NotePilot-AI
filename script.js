
const button = document.querySelector("#summarizeBtn");
const notesInput = document.querySelector("#notes");
const result = document.querySelector("#result");


function appendInlineMarkdown(container, text) {
    const normalizedText = text.replace(/\\([*_#])/g, "$1");
    const boldPattern = /(\*\*|__)(.+?)\1/g;
    let lastIndex = 0;
    let match;

    while ((match = boldPattern.exec(normalizedText)) !== null) {
        container.append(
            document.createTextNode(
                normalizedText.slice(lastIndex, match.index)
            )
        );

        const bold = document.createElement("strong");
        bold.textContent = match[2];
        container.append(bold);
        lastIndex = boldPattern.lastIndex;
    }

    container.append(
        document.createTextNode(normalizedText.slice(lastIndex))
    );
}


function renderSummary(markdown) {
    result.replaceChildren();

    const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
    let paragraphLines = [];
    let list = null;

    function flushParagraph() {
        if (paragraphLines.length === 0) {
            return;
        }

        const paragraph = document.createElement("p");
        appendInlineMarkdown(paragraph, paragraphLines.join(" "));
        result.append(paragraph);
        paragraphLines = [];
    }

    function closeList() {
        list = null;
    }

    for (const line of lines) {
       const headingMatch = line.match(/^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/);
const bulletMatch = line.match(/^\s*[-*+]\s+(.+)$/);
        if (line.trim() === "") {
            flushParagraph();
            closeList();
            continue;
        }

        if (headingMatch) {
            flushParagraph();
            closeList();

            const heading = document.createElement(`h${headingMatch[1].length}`);
            appendInlineMarkdown(heading, headingMatch[2]);
            result.append(heading);
            continue;
        }

        if (bulletMatch) {
            flushParagraph();

            if (!list) {
                list = document.createElement("ul");
                result.append(list);
            }

            const item = document.createElement("li");
            appendInlineMarkdown(item, bulletMatch[1]);
            list.append(item);
            continue;
        }

        closeList();
        paragraphLines.push(line.trim());
    }

    flushParagraph();
}

button.addEventListener("click", async function () {
    const notes = notesInput.value.trim();

    if (notes === "") {
        result.textContent = "Please enter your notes first!";
        return;
    }

    result.textContent = "Sending notes to the backend...";

    try {
const response = await fetch("http://localhost:8080/api/notes", {            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ notes: notes })
        });

        const message = await response.text();

if (!response.ok) {
    result.textContent = `Error (${response.status}): ${message}`;
    return;
}

renderSummary(message);
    } catch (error) {
        result.textContent =
            "Could not connect to the backend. Check that Spring Boot is running.";
        console.error(error);
    }
});
