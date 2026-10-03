
const button = document.querySelector("#summarizeBtn");
const notesInput = document.querySelector("#notes");
const result = document.querySelector("#result");

button.addEventListener("click", async function () {
    const notes = notesInput.value.trim();

    if (notes === "") {
        result.textContent = "Please enter your notes first!";
        return;
    }

    result.textContent = "Sending notes to the backend...";

    try {
        const response = await fetch("http://localhost:8080/api/notes", {
            method: "POST",
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

result.textContent = message;
    } catch (error) {
        result.textContent =
            "Could not connect to the backend. Check that Spring Boot is running.";
        console.error(error);
    }
});
