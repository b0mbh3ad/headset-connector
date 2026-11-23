FROM node:24-alpine

WORKDIR /app
COPY server.js admin.html package.json ./

ENV PORT=8787
ENV DATA_DIR=/data
EXPOSE 8787
VOLUME ["/data"]

CMD ["node", "server.js"]
