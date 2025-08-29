package br.com.ia.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ObjectToBytesConverter {

	/**
	 * Converte qualquer objeto serializável em array de bytes
	 * 
	 * @param obj Objeto a ser convertido (deve implementar Serializable)
	 * @return Array de bytes representando o objeto
	 * @throws IOException              Se houver erro na serialização
	 * @throws IllegalArgumentException Se o objeto não for serializável
	 */
	public static byte[] objectToBytes(Object obj) throws IOException {
		if (obj == null) {
			throw new IllegalArgumentException("Objeto não pode ser null");
		}

		if (!(obj instanceof Serializable)) {
			throw new IllegalArgumentException("Objeto deve implementar Serializable");
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos)) {

			oos.writeObject(obj);
			oos.flush();
			return baos.toByteArray();
		}
	}

	/**
	 * Converte array de bytes de volta para objeto
	 * 
	 * @param bytes Array de bytes do objeto serializado
	 * @return Objeto deserializado
	 * @throws IOException            Se houver erro na deserialização
	 * @throws ClassNotFoundException Se a classe do objeto não for encontrada
	 */
	public static Object bytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("Array de bytes não pode ser null ou vazio");
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				ObjectInputStream ois = new ObjectInputStream(bais)) {

			return ois.readObject();
		}
	}

	/**
	 * Converte objeto para Base64 (útil para transmissão/armazenamento)
	 * 
	 * @param obj Objeto a ser convertido
	 * @return String Base64 do objeto
	 * @throws IOException Se houver erro na serialização
	 */
	public static String objectToBase64(Object obj) throws IOException {
		byte[] bytes = objectToBytes(obj);
		return Base64.getEncoder().encodeToString(bytes);
	}

	/**
	 * Converte Base64 de volta para objeto
	 * 
	 * @param base64 String Base64 do objeto
	 * @return Objeto deserializado
	 * @throws IOException            Se houver erro na deserialização
	 * @throws ClassNotFoundException Se a classe do objeto não for encontrada
	 */
	public static Object base64ToObject(String base64) throws IOException, ClassNotFoundException {
		if (base64 == null || base64.trim().isEmpty()) {
			throw new IllegalArgumentException("String Base64 não pode ser null ou vazia");
		}

		byte[] bytes = Base64.getDecoder().decode(base64);
		return bytesToObject(bytes);
	}

	/**
	 * Versão genérica com tipo específico para deserialização
	 * 
	 * @param bytes Array de bytes
	 * @param clazz Classe esperada do objeto
	 * @return Objeto do tipo especificado
	 * @throws IOException            Se houver erro na deserialização
	 * @throws ClassNotFoundException Se a classe não for encontrada
	 * @throws ClassCastException     Se o objeto não for do tipo esperado
	 */
	public static <T> T bytesToObject(byte[] bytes, Class<T> clazz) throws IOException, ClassNotFoundException {
		Object obj = bytesToObject(bytes);
		return clazz.cast(obj);
	}

	/**
	 * Versão genérica Base64 com tipo específico
	 * 
	 * @param base64 String Base64
	 * @param clazz  Classe esperada do objeto
	 * @return Objeto do tipo especificado
	 */
	public static <T> T base64ToObject(String base64, Class<T> clazz) throws IOException, ClassNotFoundException {
		Object obj = base64ToObject(base64);
		return clazz.cast(obj);
	}

}
