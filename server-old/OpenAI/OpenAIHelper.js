import { OpenAI } from "openai";
import dotenv from "dotenv";

dotenv.config();

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

exports.generateChatResponse = async function(prompt, model = "gpt-4") {
    try {
      const response = await openai.chat.completions.create({
        model,
        messages: [{ role: "user", content: prompt }],
      });

      return response.choices[0]?.message?.content;
    } catch (error) {
      throw new Error("Error generating text:", error);
    }
};

exports.generateResponse = async function (prompt, model_val) {
    try {
      const response = await openai.completions.create({
        model: model_val, 
        prompt: prompt,
        max_tokens: 4000,
      });

      return response.choices[0]?.text;
    } catch (error) {
      throw new Error("Error generating text:", error);
    }
};